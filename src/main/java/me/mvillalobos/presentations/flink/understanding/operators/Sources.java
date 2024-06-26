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
				// ACCORDING TO DOCUMENTATION
				// https://nightlies.apache.org/flink/flink-docs-release-1.19/docs/connectors/datastream/kafka/#idleness
				// The Kafka Source does not go automatically in an idle state if the parallelism is higher than
				// the number of partitions. You will either need to lower the parallelism or add an idle timeout to
				// the watermark strategy. If no records flow in a partition of a stream for that amount of time,
				// then that partition is considered “idle” and will not hold back the progress of watermarks in
				// downstream operators.
				// HENCE
				// We configured idleness detection.
				WatermarkStrategy.<RawTimeSeries>forMonotonousTimestamps()
						.withIdleness(Duration.ofMillis(timeSeriesKafkaSourceIdlenessMs)),
				"time-series kafka source"
		);
	}
}
