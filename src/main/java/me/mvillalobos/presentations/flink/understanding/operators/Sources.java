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

public class Sources {
	public DataStream<RawTimeSeries> timeSeriesSource(StreamExecutionEnvironment streamEnv, Parameters parameters) {
		final String kafkaBootstrapServers = parameters.getKakfaBootStrapServers();
		final String kafkaGroupId = parameters.getTimeSeriesKafkaGroupId();
		final String kafkaTopic = parameters.getTimeSeriesKafkaTopic();
		final DataStream<RawTimeSeries> timeSeriesSource = buildTimeSeriesSource(kafkaBootstrapServers, kafkaTopic, kafkaGroupId, streamEnv)
				.uid("time-series kafka-source");
		return timeSeriesSource;
	}

	private DataStreamSource<RawTimeSeries> buildTimeSeriesSource(String kafkaBootstrapServers, String kafkaTopic, String kafkaGroupId, StreamExecutionEnvironment env) {
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
}
