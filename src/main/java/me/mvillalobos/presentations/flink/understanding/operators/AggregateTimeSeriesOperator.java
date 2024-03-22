package me.mvillalobos.presentations.flink.understanding.operators;

import me.mvillalobos.presentations.flink.understanding.domain.TimeSeries;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Instant;

public class AggregateTimeSeriesOperator extends ProcessWindowFunction<TimeSeries, TimeSeries, String, TimeWindow> {
	@Override
	public void process(
			String key,
			ProcessWindowFunction<TimeSeries, TimeSeries, String, TimeWindow>.Context context,
			Iterable<TimeSeries> elements, Collector<TimeSeries> out
	) throws Exception {
		final Instant eventTime = Instant.ofEpochMilli(context.window().getEnd());
		for (TimeSeries element : elements) {
			element.setStep(eventTime);
			element.setEventTime(eventTime);
			out.collect(element);
		}
	}
}
