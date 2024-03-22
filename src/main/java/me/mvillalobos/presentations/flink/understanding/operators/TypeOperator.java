package me.mvillalobos.presentations.flink.understanding.operators;

import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import me.mvillalobos.presentations.flink.understanding.domain.TimeSeries;
import me.mvillalobos.presentations.flink.understanding.domain.TimeSeriesType;
import org.apache.flink.api.common.functions.MapFunction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypeOperator implements MapFunction<RawTimeSeries, TimeSeries> {

	final Pattern pattern = Pattern.compile("pu136mod-n(?<type>[ids])-[0-9]+");
	@Override
	public TimeSeries map(RawTimeSeries value) throws Exception {
		final TimeSeriesType timeSeriesType = calcTimeSeriesType(value);

		final TimeSeries timeSeries = TimeSeries.builder()
				.step(value.getStep())
				.name(value.getName())
				.value(value.getValue())
				.eventTime(value.getEventTime())
				.type(timeSeriesType)
				.build();

		return timeSeries;
	}

	private TimeSeriesType calcTimeSeriesType(RawTimeSeries value) {
		final TimeSeriesType timeSeriesType;
		final Matcher matcher = pattern.matcher(value.getName());
		if (matcher.find()) {
			final String type = matcher.group("type");
			if ("i".equals(type) || "d".equals(type)) {
				timeSeriesType = TimeSeriesType.numeric;
			} else if ("s".equals(type)){
				timeSeriesType = TimeSeriesType.string;
			} else {
				timeSeriesType = null;
			}
		} else {
			timeSeriesType = null;
		}
		return timeSeriesType;
	}
}
