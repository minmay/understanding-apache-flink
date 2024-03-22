package me.mvillalobos.presentations.flink.understanding.operators;

import me.mvillalobos.presentations.flink.understanding.domain.TimeSeries;
import me.mvillalobos.presentations.flink.understanding.util.InfluxEncoder;
import org.apache.flink.api.common.functions.MapFunction;

public class LineOperator implements MapFunction<TimeSeries, String> {
	private final InfluxEncoder encoder = new InfluxEncoder();
	@Override
	public String map(TimeSeries value) throws Exception {
		return encoder.timeSeries(value);
	}
}
