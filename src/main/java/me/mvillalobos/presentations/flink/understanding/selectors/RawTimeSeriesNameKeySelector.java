package me.mvillalobos.presentations.flink.understanding.selectors;

import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import org.apache.flink.api.java.functions.KeySelector;

public class RawTimeSeriesNameKeySelector implements KeySelector<RawTimeSeries, String> {
	@Override
	public String getKey(RawTimeSeries value) throws Exception {
		return value.getName();
	}
}
