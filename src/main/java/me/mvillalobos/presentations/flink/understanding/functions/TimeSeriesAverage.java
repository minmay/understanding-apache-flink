package me.mvillalobos.presentations.flink.understanding.functions;

import me.mvillalobos.presentations.flink.understanding.domain.TimeSeries;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple3;

public class TimeSeriesAverage implements AggregateFunction<TimeSeries, Tuple3<TimeSeries, Double, Long>, TimeSeries> {
	@Override
	public Tuple3<TimeSeries, Double, Long> createAccumulator() {
		return new Tuple3<>(null, 0D, 0L);
	}

	@Override
	public Tuple3<TimeSeries, Double, Long> add(TimeSeries value, Tuple3<TimeSeries, Double, Long> accumulator) {
		accumulator.f0 = value;
		accumulator.f1 += Double.parseDouble(value.getValue());
		accumulator.f2 += 1;
		return accumulator;
	}

	@Override
	public TimeSeries getResult(Tuple3<TimeSeries, Double, Long> accumulator) {
		final double value = calcAverage(accumulator.f1, accumulator.f2);
		accumulator.f0.setValue(Double.toString(value));
		return accumulator.f0;
	}

	@Override
	public Tuple3<TimeSeries, Double, Long> merge(
			Tuple3<TimeSeries, Double, Long> first, Tuple3<TimeSeries, Double, Long> second
	) {
		first.f1 += second.f1;
		first.f2 += second.f2;
		return first;
	}

	private double calcAverage(double sum, long count) {
		return  sum / count;
	}
}
