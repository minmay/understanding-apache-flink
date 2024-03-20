package me.mvillalobos.presentations.flink.understanding;

import com.twitter.chill.java.UnmodifiableCollectionSerializer;
import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import me.mvillalobos.presentations.flink.understanding.functions.EnrichRawTimeSeriesWithEventtime;
import me.mvillalobos.presentations.flink.understanding.functions.MapRawTimeSeriesToGenericRecord;
import me.mvillalobos.presentations.flink.understanding.io.CSVDeserializer;
import org.apache.avro.Schema;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.avro.typeutils.GenericRecordAvroTypeInfo;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamStatementSet;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;

import java.io.IOException;

public class UnderstandingApacheFlinkApp {

	private final static String TIME_SERIES_DDL_TEMPLATE =
			"CREATE TABLE time_series (\n" +
			"    step TIMESTAMP_LTZ,\n" +
			"    name VARCHAR,\n" +
			"    tag_value VARCHAR,\n" +
			"    event_time TIMESTAMP_LTZ,\n" +
			"    step_year INT,\n" +
			"    step_date VARCHAR\n" +
			") PARTITIONED BY (\n" +
			"    step_year,\n" +
			"    step_date\n" +
			") WITH (\n" +
			"    'connector' = 'filesystem',\n" +
			"    'path' = '%s',\n" + 				// path to a directory
			"    'format' = 'parquet'\n" +
			");";
	public static void main(String args[]) throws Exception {
		final StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.getExecutionEnvironment();

		final Class<?> unmodifiableCollectionSerializer = Class.forName("java.util.Collections$UnmodifiableCollection");
		streamEnv.getConfig().addDefaultKryoSerializer(unmodifiableCollectionSerializer, UnmodifiableCollectionSerializer.class);

		final ParameterTool parameters = ParameterTool.fromArgs(args);
		streamEnv.getConfig().setGlobalJobParameters(parameters);

		final String kafkaBootstrapServers = parameters.get("kafka.bootstrap-servers", "data-gen-agent-kafka-bootstrap:9092");
		final String kafkaGroupId = parameters.get("kafka.group-id", "understanding-flink");
		final String kafkaTopic = parameters.get("kafka.topic", "time-series");
		final String longTermStoreSinkPath = parameters.get("long-term-store.sink-path", "s3://long-term-store");
		final boolean longTermStoreSinkPathSqlApiEnabled = parameters.getBoolean("long-term-store.sink-path.sql-api.enabled", true);

		final DataStream<RawTimeSeries> timeSeriesSource = buildTimeSeriesSource(kafkaBootstrapServers, kafkaTopic, kafkaGroupId, streamEnv)
				.uid("time-series kafka-source");

		final DataStream<RawTimeSeries> timeSeriesStream = timeSeriesSource.keyBy(
				new NameKeySelector()
		).process(new EnrichRawTimeSeriesWithEventtime())
				.name("enrich time-series with event time")
				.uid("enrich-time-series-with-event-time");

		if (longTermStoreSinkPathSqlApiEnabled) {
			sinkToTimeSeriesSink(streamEnv, timeSeriesStream, longTermStoreSinkPath);
		} else {
			final Schema schema = buildSchema();
			final DataStream<GenericRecord> avroRawTimeSeriesStream = timeSeriesStream.map(MapRawTimeSeriesToGenericRecord.create(schema))
					.returns(new GenericRecordAvroTypeInfo(schema))
					.name("map time-series with event-time to avro generic record")
					.uid("map-time-series-with-event-time-to-avro-generic-record");

			final FileSink<GenericRecord> longTermStoreSink = buildLongTermStore(schema, longTermStoreSinkPath);
			avroRawTimeSeriesStream.sinkTo(longTermStoreSink)
					.name("save-to-long-term-sink")
					.uid("save to long-term sink");
		}

		streamEnv.execute("Understanding Apache Flink");
	}

	private static FileSink<GenericRecord> buildLongTermStore(Schema schema, String longTermStoreSinkPath) {
		GenericData.get().addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());
		return FileSink
				.forBulkFormat(
						new Path(longTermStoreSinkPath), AvroParquetWriters.forGenericRecord(schema))
				.build();
	}

	@VisibleForTesting
	public static Schema buildSchema() throws IOException {
		return new Schema.Parser().parse(MapRawTimeSeriesToGenericRecord.class.getResourceAsStream("/RawTimeSeries.avsc"));
	}

	private static DataStreamSource<RawTimeSeries> buildTimeSeriesSource(String kafkaBootstrapServers, String kafkaTopic, String kafkaGroupId, StreamExecutionEnvironment env) {
		KafkaSource<RawTimeSeries> kafkaSource = KafkaSource.<RawTimeSeries>builder()
				.setBootstrapServers(kafkaBootstrapServers)
				.setTopics(kafkaTopic)
				.setGroupId(kafkaGroupId)
				.setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
				.setDeserializer(new CSVDeserializer())
				.build();

		return env.fromSource(
				kafkaSource,
				WatermarkStrategy.forMonotonousTimestamps(),
				"time-series kafka source"
		);
	}

	private static void sinkToTimeSeriesSink(StreamExecutionEnvironment streamEnv, DataStream<RawTimeSeries> rawTimeSeriesStream, String longTermStoreSinkPath) {
		final StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);
		final StreamStatementSet statementSet = tableEnv.createStatementSet();

		createTimeSeriesTable(tableEnv, longTermStoreSinkPath);

		final Table timeSeriesTable = tableEnv.fromDataStream(
				rawTimeSeriesStream.keyBy(new PartitionKeySelector()),
				org.apache.flink.table.api.Schema.newBuilder()
						.column("step", DataTypes.TIMESTAMP_LTZ())
						.column("name", DataTypes.STRING())
						.column("value", DataTypes.STRING())
						.column("eventTime", DataTypes.TIMESTAMP_LTZ())
						.column("stepYear", DataTypes.INT())
						.column("stepDate", DataTypes.STRING())
						.build()
		);

		statementSet.add(timeSeriesTable.insertInto("time_series"));
		statementSet.attachAsDataStream();
	}

	public static String buildTimeSeriesDDL(String path) {
		return String.format(TIME_SERIES_DDL_TEMPLATE, path);
	}

	public static TableResult createTimeSeriesTable(StreamTableEnvironment tableEnv, String path) {
		final TableResult tableResult = tableEnv.executeSql(buildTimeSeriesDDL(path));
		return tableResult;
	}

	private static class PartitionKeySelector implements KeySelector<RawTimeSeries, Tuple2<Integer, String>> {
		@Override
		public Tuple2<Integer, String> getKey(RawTimeSeries value) throws Exception {
			return Tuple2.of(value.getStepYear(), value.getDate());
		}
	}

	private static class NameKeySelector implements KeySelector<RawTimeSeries, String> {
		@Override
		public String getKey(RawTimeSeries value) throws Exception {
			return value.getName();
		}
	}
}
