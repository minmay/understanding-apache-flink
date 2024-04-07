package me.mvillalobos.presentations.flink.understanding.util;

import lombok.Getter;
import org.apache.flink.api.java.utils.ParameterTool;

@Getter
public class Parameters {

	private final String kakfaBootStrapServers;
	private final String timeSeriesKafkaGroupId;
	private final String timeSeriesKafkaTopic;
	private final String telegrafKafkaTopic;
	private final long timeSeriesKafkaSourceIdlenessMs;
	private final String longTermStoreSinkPath;
	private final boolean longTermStoreSinkPathSqlApiEnabled;

	public Parameters(ParameterTool parameters) {
		this.kakfaBootStrapServers =  parameters.get("kafka.bootstrap-servers", "understanding-flink-kafka-bootstrap:9092");
		this.timeSeriesKafkaGroupId = parameters.get("time-series.kafka.group-id", "understanding-flink");
		this.timeSeriesKafkaTopic = parameters.get("time-series.kafka.topic", "time-series");
		this.timeSeriesKafkaSourceIdlenessMs = parameters.getLong("time-series.kafka.source.idleness-ms", 30000L);
		this.telegrafKafkaTopic = parameters.get("telegraf.kafka.topic", "telegraf");
		this.longTermStoreSinkPath = parameters.get("long-term-store.sink-path", "s3://long-term-store");
		this.longTermStoreSinkPathSqlApiEnabled = parameters.getBoolean("long-term-store.sink-path.sql-api.enabled", true);
	}
}
