package me.mvillalobos.presentations.flink.understanding.domain;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
public class TimeSeries implements Serializable {
	private Instant step;
	private String name;
	private TimeSeriesType type;
	private String value;
	private Instant eventTime;
}
