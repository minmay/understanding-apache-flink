package me.mvillalobos.presentations.flink.understanding.functions;

import org.apache.flink.api.common.functions.ReduceFunction;

public class ReduceLine implements ReduceFunction<String> {
	@Override
	public String reduce(String first, String second) throws Exception {
		return first + "\n" + second;
	}
}
