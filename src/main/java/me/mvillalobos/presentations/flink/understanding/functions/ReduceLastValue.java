package me.mvillalobos.presentations.flink.understanding.functions;

import me.mvillalobos.presentations.flink.understanding.domain.TimeSeries;
import org.apache.flink.api.common.functions.ReduceFunction;

public class ReduceLastValue implements ReduceFunction<TimeSeries> {
	@Override
	public TimeSeries reduce(TimeSeries first, TimeSeries second) throws Exception {
		final TimeSeries value;

		if (second.getStep().isAfter(first.getStep())) {
			first.setValue(second.getValue());
			value = first;
		} else {
			second.setValue(first.getValue());
			value = second;
		}

		return value;
	}
}
