package me.mvillalobos.presentations.flink.understanding.operators;

import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import me.mvillalobos.presentations.flink.understanding.selectors.PartitionKeySelector;
import me.mvillalobos.presentations.flink.understanding.util.Parameters;
import org.apache.avro.Schema;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamStatementSet;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.io.IOException;

public class Sinks {

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
			"    'path' = '%s',\n" +                // path to a directory
			"    'format' = 'parquet'\n" +
			");";

	public void timeSeriesSink(StreamExecutionEnvironment streamEnv, DataStream<RawTimeSeries> timeSeriesStream, Parameters parameters) throws IOException {
		final boolean longTermStoreSinkPathSqlApiEnabled = parameters.isLongTermStoreSinkPathSqlApiEnabled();

		if (longTermStoreSinkPathSqlApiEnabled) {
			timeSeriesTableSink(streamEnv, timeSeriesStream, parameters);
		} else {
			final Schema schema = buildSchema();
			final Operators operators = new Operators();
			final DataStream<GenericRecord> avroRawTimeSeriesStream = operators.genericRecordOperator(timeSeriesStream, schema);
			timeSeriesDataStreamSink(avroRawTimeSeriesStream, parameters, schema);
		}
	}

	private void timeSeriesDataStreamSink(DataStream<GenericRecord> avroRawTimeSeriesStream, Parameters parameters, Schema schema) {
		final String longTermStoreSinkPath = parameters.getLongTermStoreSinkPath();
		final FileSink<GenericRecord> longTermStoreSink = buildLongTermStore(schema, longTermStoreSinkPath);
		avroRawTimeSeriesStream.sinkTo(longTermStoreSink)
				.name("save-to-long-term-sink")
				.uid("save to long-term sink");
	}

	private FileSink<GenericRecord> buildLongTermStore(Schema schema, String longTermStoreSinkPath) {
		GenericData.get().addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());
		return FileSink
				.forBulkFormat(
						new Path(longTermStoreSinkPath), AvroParquetWriters.forGenericRecord(schema))
				.build();
	}

	public void telegrafSink(DataStream<String> collectedLineStream, Parameters parameters) {
		final KafkaSink<String> telegrafKafkaProducerSink = buildTelegrafKafkaSink(parameters);
		collectedLineStream.sinkTo(telegrafKafkaProducerSink)
				.name("telegraf")
				.uid("telegraf");
	}

	private KafkaSink<String> buildTelegrafKafkaSink(Parameters parameters) {
		final KafkaSink<String> telegrafKafkaProducerSink = KafkaSink.<String>builder()
				.setBootstrapServers(parameters.getKakfaBootStrapServers())
				.setRecordSerializer(KafkaRecordSerializationSchema.builder()
						.setTopic(parameters.getTelegrafKafkaTopic())
						.setValueSerializationSchema(new SimpleStringSchema())
						.build()
				)
				.setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
				.build();
		return telegrafKafkaProducerSink;
	}

	private void timeSeriesTableSink(StreamExecutionEnvironment streamEnv, DataStream<RawTimeSeries> rawTimeSeriesStream, Parameters parameters) {
		final String longTermStoreSinkPath = parameters.getLongTermStoreSinkPath();
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

	private String buildTimeSeriesDDL(String path) {
		return String.format(TIME_SERIES_DDL_TEMPLATE, path);
	}

	private void createTimeSeriesTable(StreamTableEnvironment tableEnv, String path) {
		tableEnv.executeSql(buildTimeSeriesDDL(path));
	}

	private Schema buildSchema() throws IOException {
		return new Schema.Parser().parse(MapRawTimeSeriesToGenericRecordOperator.class.getResourceAsStream("/RawTimeSeries.avsc"));
	}
}
