package me.mvillalobos.presentations.flink.understanding.operators;

import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import me.mvillalobos.presentations.flink.understanding.io.CSVDeserializer;
import me.mvillalobos.presentations.flink.understanding.util.Parameters;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;

import java.time.Duration;

public class Sources {
	public DataStream<RawTimeSeries> timeSeriesSource(StreamExecutionEnvironment streamEnv, Parameters parameters) {
		final String kafkaBootstrapServers = parameters.getKakfaBootStrapServers();
		final String kafkaGroupId = parameters.getTimeSeriesKafkaGroupId();
		final String kafkaTopic = parameters.getTimeSeriesKafkaTopic();
		final long timeSeriesKafkaSourceIdlenessMs = parameters.getTimeSeriesKafkaSourceIdlenessMs();
		final DataStream<RawTimeSeries> timeSeriesSource = buildTimeSeriesSource(kafkaBootstrapServers, kafkaTopic, kafkaGroupId, timeSeriesKafkaSourceIdlenessMs, streamEnv)
				.uid("time-series kafka-source");
		return timeSeriesSource;
	}

	private DataStreamSource<RawTimeSeries> buildTimeSeriesSource(
			String kafkaBootstrapServers,
			String kafkaTopic,
			String kafkaGroupId,
			long timeSeriesKafkaSourceIdlenessMs,
			StreamExecutionEnvironment env
	) {
		KafkaSource<RawTimeSeries> kafkaSource = KafkaSource.<RawTimeSeries>builder()
				.setBootstrapServers(kafkaBootstrapServers)
				.setTopics(kafkaTopic)
				.setGroupId(kafkaGroupId)
				.setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
				.setDeserializer(new CSVDeserializer())
				.build();

		return env.fromSource(
				kafkaSource,
				// https://stackoverflow.com/questions/73825459/why-flink-1-15-2-showing-no-watermark-watermarks-are-only-available-if-eventtim
				// if "operators that read from Kafka had lesser parallelism than the number of Kafka partitions" then
				// you must configure idleness.
				WatermarkStrategy.<RawTimeSeries>forMonotonousTimestamps()
						.withIdleness(Duration.ofMillis(timeSeriesKafkaSourceIdlenessMs)),
				"time-series kafka source"
		);
	}
}
