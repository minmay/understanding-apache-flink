package me.mvillalobos.presentations.flink.understanding.util;

import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import me.mvillalobos.presentations.flink.understanding.domain.TimeSeries;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.Instant;

public class InfluxEncoder implements Serializable {

	private final BigInteger NANO_MULTIPLIER = BigInteger.valueOf(1000000L);

	public String rawTimeSeries(RawTimeSeries element) {
		return format(element.getStep(), element.getName(), element.getValue(), element.getEventTime());
	}

	public String timeSeries(TimeSeries element) {
		return format(element.getStep(), element.getName(), element.getValue(), element.getEventTime());
	}

	public String field(String value) {
		return value
				.replace("\"", "\\\"")
				.replace("\\", "\\\\");
	}

	public String tag(String value) {
		return value
				.replace(",", "\\,")
				.replace("=", "\\=")
				.replace(" ", "\\ ");
	}

	public String instant(Instant instant) {
		return epoch(instant.toEpochMilli());
	}

	public String epoch(long epoch) {
		return BigInteger.valueOf(epoch).multiply(NANO_MULTIPLIER).toString();
	}

	public String format(Instant step, String name, String value, Instant eventTime) {
		final StringBuilder line = new StringBuilder()
				.append("tags,name=").append(tag(name))
				.append(" event_time=").append(instant(eventTime))
				.append(",tag_value=").append('"').append(field(value)).append('"')
				.append(" ").append(instant(step));
		return line.toString();
	}
}
