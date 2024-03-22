package me.mvillalobos.presentations.flink.understanding.operators;

import me.mvillalobos.presentations.flink.understanding.domain.TimeSeries;
import me.mvillalobos.presentations.flink.understanding.domain.TimeSeriesType;
import org.apache.flink.api.common.functions.FilterFunction;

public class NumericTypeFilterOperator implements FilterFunction<TimeSeries>  {
	@Override
	public boolean filter(TimeSeries value) throws Exception {
		return TimeSeriesType.numeric == value.getType();
	}
}
