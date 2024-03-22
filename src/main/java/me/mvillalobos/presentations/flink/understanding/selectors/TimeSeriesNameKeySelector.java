package me.mvillalobos.presentations.flink.understanding.selectors;

import me.mvillalobos.presentations.flink.understanding.domain.TimeSeries;
import org.apache.flink.api.java.functions.KeySelector;

public class TimeSeriesNameKeySelector implements KeySelector<TimeSeries, String> {
	@Override
	public String getKey(TimeSeries value) throws Exception {
		return value.getName();
	}
}
