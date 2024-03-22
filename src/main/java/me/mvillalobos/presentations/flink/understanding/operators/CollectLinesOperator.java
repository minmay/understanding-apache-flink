package me.mvillalobos.presentations.flink.understanding.operators;

import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public class CollectLinesOperator implements AllWindowFunction<String, String, TimeWindow> {
	@Override
	public void apply(TimeWindow window, Iterable<String> lines, Collector<String> out) throws Exception {
		for (String line : lines) {
			if (!line.isBlank()) {
				out.collect(line);
			}
		}
	}
}
