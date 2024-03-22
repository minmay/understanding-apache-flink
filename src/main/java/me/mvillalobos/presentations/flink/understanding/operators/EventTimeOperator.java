package me.mvillalobos.presentations.flink.understanding.operators;

import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.time.Instant;

public class EventTimeOperator extends KeyedProcessFunction<String, RawTimeSeries, RawTimeSeries> {
	@Override
	public void processElement(
			RawTimeSeries value,
			KeyedProcessFunction<String, RawTimeSeries, RawTimeSeries>.Context ctx,
			Collector<RawTimeSeries> out
	) throws Exception {
		final Instant eventTime = Instant.ofEpochMilli(ctx.timestamp());
		value.setEventTime(eventTime);
		out.collect(value);
	}
}
