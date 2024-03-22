package me.mvillalobos.presentations.flink.understanding;

import com.twitter.chill.java.UnmodifiableCollectionSerializer;
import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import me.mvillalobos.presentations.flink.understanding.domain.TimeSeries;
import me.mvillalobos.presentations.flink.understanding.operators.Operators;
import me.mvillalobos.presentations.flink.understanding.operators.Sinks;
import me.mvillalobos.presentations.flink.understanding.operators.Sources;
import me.mvillalobos.presentations.flink.understanding.util.Parameters;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class UnderstandingApacheFlinkApp {

	public static void main(String[] args) throws Exception {
		final ParameterTool parameterTool = ParameterTool.fromArgs(args);
		final Parameters parameters = new Parameters(parameterTool);
		final StreamExecutionEnvironment streamEnv = streamEnv(parameterTool);
		final Sources sources = new Sources();
		final Operators operators = new Operators();
		final Sinks sinks = new Sinks();

		final DataStream<RawTimeSeries> rawTimeSeriesSource = sources.timeSeriesSource(streamEnv, parameters);
		final DataStream<RawTimeSeries> rawtimeSeriesStream = operators.eventTimeOperator(rawTimeSeriesSource);
		final DataStream<TimeSeries> timeSeriesStream = operators.typeOperator(rawTimeSeriesSource);
		final DataStream<TimeSeries> numericTimeSeriesStream = operators.numericFilterOperator(timeSeriesStream);
		final DataStream<TimeSeries> stringTimeSeriesStream = operators.stringFilterOperator(timeSeriesStream);

		final DataStream<TimeSeries> averageTimeSeries = operators.averageOperator(numericTimeSeriesStream);
		final DataStream<TimeSeries> lastTimeSeries = operators.lastOperator(stringTimeSeriesStream);
		final DataStream<TimeSeries> unionStream = averageTimeSeries.union(lastTimeSeries);
		final DataStream<String> lineStream = operators.lineOperator(unionStream);

		final DataStream<String> collectedLineStream = operators.collectLinesOperator(lineStream);

		sinks.telegrafSink(collectedLineStream, parameters);
		sinks.timeSeriesSink(streamEnv, rawtimeSeriesStream, parameters);

		streamEnv.execute("Understanding Apache Flink");
	}

	private static StreamExecutionEnvironment streamEnv(ParameterTool parameters) throws ClassNotFoundException {
		final StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.getExecutionEnvironment();
		final Class<?> unmodifiableCollectionSerializer = Class.forName("java.util.Collections$UnmodifiableCollection");
		streamEnv.getConfig().addDefaultKryoSerializer(unmodifiableCollectionSerializer, UnmodifiableCollectionSerializer.class);
		streamEnv.getConfig().setGlobalJobParameters(parameters);
		return streamEnv;
	}
}
