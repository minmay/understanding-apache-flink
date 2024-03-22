package me.mvillalobos.presentations.flink.understanding.selectors;

import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;

public class PartitionKeySelector implements KeySelector<RawTimeSeries, Tuple2<Integer, String>> {
	@Override
	public Tuple2<Integer, String> getKey(RawTimeSeries value) throws Exception {
		return Tuple2.of(value.getStepYear(), value.getStepDate());
	}
}
