package me.mvillalobos.presentations.flink.understanding.functions;

import org.apache.flink.api.common.functions.AggregateFunction;

public class CollectLines implements AggregateFunction<String, StringBuilder, String> {
	@Override
	public StringBuilder createAccumulator() {
		return new StringBuilder();
	}

	@Override
	public StringBuilder add(String value, StringBuilder accumulator) {
		return accumulator.append('\n').append(value);
	}

	@Override
	public String getResult(StringBuilder accumulator) {
		return accumulator.toString();
	}

	@Override
	public StringBuilder merge(StringBuilder first, StringBuilder second) {
		return first.append('\n').append(second);
	}
}
