package me.mvillalobos.presentations.flink.understanding.operators;

import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import me.mvillalobos.presentations.flink.understanding.domain.TimeSeries;
import me.mvillalobos.presentations.flink.understanding.functions.CollectLines;
import me.mvillalobos.presentations.flink.understanding.functions.ReduceLastValue;
import me.mvillalobos.presentations.flink.understanding.functions.TimeSeriesAverage;
import me.mvillalobos.presentations.flink.understanding.selectors.TimeSeriesNameKeySelector;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.formats.avro.typeutils.GenericRecordAvroTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.triggers.CountTrigger;
import org.apache.flink.streaming.api.windowing.triggers.ProcessingTimeoutTrigger;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;

import java.time.Duration;

public class Operators {
	public DataStream<GenericRecord> genericRecordOperator(DataStream<RawTimeSeries> timeSeriesStream, Schema schema) {
		final DataStream<GenericRecord> avroRawTimeSeriesStream = timeSeriesStream.map(MapRawTimeSeriesToGenericRecordOperator.create(schema))
				.returns(new GenericRecordAvroTypeInfo(schema))
				.name("map time-series with event-time to avro generic record operator")
				.uid("map-time-series-with-event-time-to-avro-generic-record-operator");
		return avroRawTimeSeriesStream;
	}

	public DataStream<TimeSeries> typeOperator(DataStream<RawTimeSeries> timeSeriesSource) {
		return timeSeriesSource.map(new TypeOperator())
				.name("type operator")
				.uid("type-operator");
	}

	public DataStream<TimeSeries> numericFilterOperator(DataStream<TimeSeries> timeSeriesStream) {
		return timeSeriesStream.filter(new NumericTypeFilterOperator())
				.name("numeric filter operator")
				.uid("numeric-filter-operator");
	}

	public DataStream<TimeSeries> stringFilterOperator(DataStream<TimeSeries> timeSeriesStream) {
		return timeSeriesStream.filter(new StringTypeFilterOperator())
				.name("string filter operator")
				.uid("string-filter-operator");
	}
	public SingleOutputStreamOperator<TimeSeries> averageOperator(DataStream<TimeSeries> numericTimeSeriesStream) {
		return numericTimeSeriesStream.keyBy(new TimeSeriesNameKeySelector())
				.window(TumblingEventTimeWindows.of(Time.minutes(1)))
				.aggregate(new TimeSeriesAverage(), new AggregateTimeSeriesOperator())
				.name("average time series")
				.uid("average-time-series");
	}

	public SingleOutputStreamOperator<TimeSeries> lastOperator(DataStream<TimeSeries> stringTimeSeriesStream) {
		return stringTimeSeriesStream.keyBy(new TimeSeriesNameKeySelector())
				.window(TumblingEventTimeWindows.of(Time.minutes(1)))
				.reduce(new ReduceLastValue(), new AggregateTimeSeriesOperator())
				.name("last time series")
				.uid("last-time-series");
	}

	public DataStream<String> lineOperator(DataStream<TimeSeries> timeSeriesSource) {
		return timeSeriesSource.map(new LineOperator())
				.name("line operator")
				.uid("line-operator");
	}

	public DataStream<String> collectLinesOperator(DataStream<String> lineStream) {
		final CountTrigger<TimeWindow> countTrigger = CountTrigger.of(1024);
		final ProcessingTimeoutTrigger<Object, TimeWindow> trigger = ProcessingTimeoutTrigger.of(countTrigger, Duration.ofMinutes(1), false, true);
		return lineStream.windowAll(TumblingEventTimeWindows.of(Time.minutes(1)))
				.trigger(trigger)
				.aggregate(new CollectLines(), new CollectLinesOperator())
				.name("collect lines")
				.uid("collect-lines");
	}
}
