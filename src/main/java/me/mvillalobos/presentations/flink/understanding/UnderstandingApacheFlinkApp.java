package me.mvillalobos.presentations.flink.understanding;

import com.twitter.chill.java.UnmodifiableCollectionSerializer;
import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import me.mvillalobos.presentations.flink.understanding.domain.TimeSeries;
import me.mvillalobos.presentations.flink.understanding.functions.CollectLines;
import me.mvillalobos.presentations.flink.understanding.functions.ReduceLastValue;
import me.mvillalobos.presentations.flink.understanding.functions.TimeSeriesAverage;
import me.mvillalobos.presentations.flink.understanding.io.CSVDeserializer;
import me.mvillalobos.presentations.flink.understanding.operators.AggregateTimeSeriesOperator;
import me.mvillalobos.presentations.flink.understanding.operators.CollectLinesOperator;
import me.mvillalobos.presentations.flink.understanding.operators.EventTimeOperator;
import me.mvillalobos.presentations.flink.understanding.operators.LineOperator;
import me.mvillalobos.presentations.flink.understanding.operators.MapRawTimeSeriesToGenericRecordOperator;
import me.mvillalobos.presentations.flink.understanding.operators.NumericTypeFilterOperator;
import me.mvillalobos.presentations.flink.understanding.operators.StringTypeFilterOperator;
import me.mvillalobos.presentations.flink.understanding.operators.TypeOperator;
import me.mvillalobos.presentations.flink.understanding.selectors.PartitionKeySelector;
import me.mvillalobos.presentations.flink.understanding.selectors.RawTimeSeriesNameKeySelector;
import me.mvillalobos.presentations.flink.understanding.selectors.TimeSeriesNameKeySelector;
import me.mvillalobos.presentations.flink.understanding.util.Parameters;
import org.apache.avro.Schema;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.avro.typeutils.GenericRecordAvroTypeInfo;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.triggers.CountTrigger;
import org.apache.flink.streaming.api.windowing.triggers.ProcessingTimeoutTrigger;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamStatementSet;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;

import java.io.IOException;
import java.time.Duration;

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
			"    'path' = '%s',\n" +                // path to a directory
			"    'format' = 'parquet'\n" +
			");";

	public static void main(String[] args) throws Exception {
		final ParameterTool parameterTool = ParameterTool.fromArgs(args);
		final Parameters parameters = new Parameters(parameterTool);
		final StreamExecutionEnvironment streamEnv = streamEnv(parameterTool);

		final DataStream<RawTimeSeries> rawTimeSeriesSource = timeSeriesSource(streamEnv, parameters);
		final DataStream<RawTimeSeries> rawtimeSeriesStream = eventTimeOperator(rawTimeSeriesSource);
		final DataStream<TimeSeries> timeSeriesStream = typeOperator(rawTimeSeriesSource);
		final DataStream<TimeSeries> numericTimeSeriesStream = numericFilterOperator(timeSeriesStream);
		final DataStream<TimeSeries> stringTimeSeriesStream = stringFilterOperator(timeSeriesStream);

		final DataStream<TimeSeries> averageTimeSeries = averageOperator(numericTimeSeriesStream);
		final DataStream<TimeSeries> lastTimeSeries = lastOperator(stringTimeSeriesStream);
		final DataStream<TimeSeries> unionStream = averageTimeSeries.union(lastTimeSeries);
		final DataStream<String> lineStream = lineOperator(unionStream);

		final DataStream<String> collectedLineStream = collectLinesOperator(lineStream);

		final KafkaSink<String> telegrafKafkaProducerSink = KafkaSink.<String>builder()
				.setBootstrapServers(parameters.getKakfaBootStrapServers())
				.setRecordSerializer(KafkaRecordSerializationSchema.builder()
						.setTopic(parameters.getTelegrafKafkaTopic())
						.setValueSerializationSchema(new SimpleStringSchema())
						.build()
				)
				.setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
				.build();

		collectedLineStream.sinkTo(telegrafKafkaProducerSink)
				.name("telegraf")
				.uid("telegraf");

		timeSeriesSink(streamEnv, rawtimeSeriesStream, parameters);

		streamEnv.execute("Understanding Apache Flink");
	}

	private static StreamExecutionEnvironment streamEnv(ParameterTool parameters) throws ClassNotFoundException {
		final StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.getExecutionEnvironment();
		final Class<?> unmodifiableCollectionSerializer = Class.forName("java.util.Collections$UnmodifiableCollection");
		streamEnv.getConfig().addDefaultKryoSerializer(unmodifiableCollectionSerializer, UnmodifiableCollectionSerializer.class);
		streamEnv.getConfig().setGlobalJobParameters(parameters);
		return streamEnv;
	}

	private static DataStream<RawTimeSeries> timeSeriesSource(StreamExecutionEnvironment streamEnv, Parameters parameters) {
		final String kafkaBootstrapServers = parameters.getKakfaBootStrapServers();
		final String kafkaGroupId = parameters.getTimeSeriesKafkaGroupId();
		final String kafkaTopic = parameters.getTimeSeriesKafkaTopic();
		final DataStream<RawTimeSeries> timeSeriesSource = buildTimeSeriesSource(kafkaBootstrapServers, kafkaTopic, kafkaGroupId, streamEnv)
				.uid("time-series kafka-source");
		return timeSeriesSource;
	}

	private static DataStream<GenericRecord> genericRecordOperator(DataStream<RawTimeSeries> timeSeriesStream, Schema schema) {
		final DataStream<GenericRecord> avroRawTimeSeriesStream = timeSeriesStream.map(MapRawTimeSeriesToGenericRecordOperator.create(schema))
				.returns(new GenericRecordAvroTypeInfo(schema))
				.name("map time-series with event-time to avro generic record operator")
				.uid("map-time-series-with-event-time-to-avro-generic-record-operator");
		return avroRawTimeSeriesStream;
	}

	private static DataStream<RawTimeSeries> eventTimeOperator(DataStream<RawTimeSeries> timeSeriesSource) {
		return timeSeriesSource.keyBy(
						new RawTimeSeriesNameKeySelector()
				).process(new EventTimeOperator())
				.name("event time operator")
				.uid("event-time-operator");
	}

	private static DataStream<TimeSeries> typeOperator(DataStream<RawTimeSeries> timeSeriesSource) {
		return timeSeriesSource.map(new TypeOperator())
				.name("type operator")
				.uid("type-operator");
	}

	private static DataStream<TimeSeries> numericFilterOperator(DataStream<TimeSeries> timeSeriesStream) {
		return timeSeriesStream.filter(new NumericTypeFilterOperator())
				.name("numeric filter operator")
				.uid("numeric-filter-operator");
	}

	private static DataStream<TimeSeries> stringFilterOperator(DataStream<TimeSeries> timeSeriesStream) {
		return timeSeriesStream.filter(new StringTypeFilterOperator())
				.name("string filter operator")
				.uid("string-filter-operator");
	}
	private static SingleOutputStreamOperator<TimeSeries> averageOperator(DataStream<TimeSeries> numericTimeSeriesStream) {
		return numericTimeSeriesStream.keyBy(new TimeSeriesNameKeySelector())
				.window(TumblingEventTimeWindows.of(Time.minutes(1)))
				.aggregate(new TimeSeriesAverage(), new AggregateTimeSeriesOperator())
				.name("average time series")
				.uid("average-time-series");
	}

	private static SingleOutputStreamOperator<TimeSeries> lastOperator(DataStream<TimeSeries> stringTimeSeriesStream) {
		return stringTimeSeriesStream.keyBy(new TimeSeriesNameKeySelector())
				.window(TumblingEventTimeWindows.of(Time.minutes(1)))
				.reduce(new ReduceLastValue(), new AggregateTimeSeriesOperator())
				.name("last time series")
				.uid("last-time-series");
	}

	private static DataStream<String> lineOperator(DataStream<TimeSeries> timeSeriesSource) {
		return timeSeriesSource.map(new LineOperator())
				.name("line operator")
				.uid("line-operator");
	}

	private static DataStream<String> collectLinesOperator(DataStream<String> lineStream) {
		final CountTrigger<TimeWindow> countTrigger = CountTrigger.of(1024);
		final ProcessingTimeoutTrigger<Object, TimeWindow> trigger = ProcessingTimeoutTrigger.of(countTrigger, Duration.ofMinutes(1), false, true);
		return lineStream.windowAll(TumblingEventTimeWindows.of(Time.minutes(1)))
				.trigger(trigger)
				.aggregate(new CollectLines(), new CollectLinesOperator())
				.name("collect lines")
				.uid("collect-lines");
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

	private static void timeSeriesSink(StreamExecutionEnvironment streamEnv, DataStream<RawTimeSeries> timeSeriesStream, Parameters parameters) throws IOException {
		final boolean longTermStoreSinkPathSqlApiEnabled = parameters.isLongTermStoreSinkPathSqlApiEnabled();

		if (longTermStoreSinkPathSqlApiEnabled) {
			timeSeriesTableSink(streamEnv, timeSeriesStream, parameters);
		} else {
			final Schema schema = buildSchema();
			final DataStream<GenericRecord> avroRawTimeSeriesStream = genericRecordOperator(timeSeriesStream, schema);
			timeSeriesDataStreamSink(avroRawTimeSeriesStream, parameters, schema);
		}
	}

	private static void timeSeriesDataStreamSink(DataStream<GenericRecord> avroRawTimeSeriesStream, Parameters parameters, Schema schema) {
		final String longTermStoreSinkPath = parameters.getLongTermStoreSinkPath();
		final FileSink<GenericRecord> longTermStoreSink = buildLongTermStore(schema, longTermStoreSinkPath);
		avroRawTimeSeriesStream.sinkTo(longTermStoreSink)
				.name("save-to-long-term-sink")
				.uid("save to long-term sink");
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
		return new Schema.Parser().parse(MapRawTimeSeriesToGenericRecordOperator.class.getResourceAsStream("/RawTimeSeries.avsc"));
	}

	private static void timeSeriesTableSink(StreamExecutionEnvironment streamEnv, DataStream<RawTimeSeries> rawTimeSeriesStream, Parameters parameters) {
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

	public static String buildTimeSeriesDDL(String path) {
		return String.format(TIME_SERIES_DDL_TEMPLATE, path);
	}

	public static void createTimeSeriesTable(StreamTableEnvironment tableEnv, String path) {
		tableEnv.executeSql(buildTimeSeriesDDL(path));
	}

}
