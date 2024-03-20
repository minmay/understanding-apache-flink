package me.mvillalobos.presentations.flink.understanding.domain;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;

@Data
public class RawTimeSeries implements Serializable {
	private final static DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private String contentType;
	private String controlStreamType;
	private Instant step;
	private String name;
	private String value;
	private int stepYear;
	private String stepDate;
	private Instant eventTime;

	public int getStepYear() {
	 	final int stepYear = (int) step.atOffset(ZoneOffset.UTC).getLong(ChronoField.YEAR);
		return stepYear;
	}

	public String getDate() {
		return DATE_FORMATTER.format(step.atOffset(ZoneOffset.UTC).toZonedDateTime());
	}
}
