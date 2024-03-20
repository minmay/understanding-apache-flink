package me.mvillalobos.presentations.flink.understanding.functions;

import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MapRawTimeSeriesToGenericRecord implements MapFunction<RawTimeSeries, GenericRecord> {

	private final static Logger logger = LoggerFactory.getLogger(MapRawTimeSeriesToGenericRecord.class);

	private final Schema schema;

	public MapRawTimeSeriesToGenericRecord(Schema schema) {
		this.schema = schema;
	}

	public Schema getSchema() {
		return schema;
	}

	@Override
	public GenericRecord map(RawTimeSeries rawTimeSeries) throws Exception {
		GenericRecord record = new GenericData.Record(schema);
		record.put("contentType", rawTimeSeries.getContentType());
		record.put("controlStreamType", rawTimeSeries.getControlStreamType());
		record.put("step", rawTimeSeries.getStep());
		record.put("name", rawTimeSeries.getName());
		record.put("value", rawTimeSeries.getValue());

		logger.info("rawTimeSeries: {}}", rawTimeSeries);
		return record;
	}

	public static MapRawTimeSeriesToGenericRecord create(Schema schema) {
		return new MapRawTimeSeriesToGenericRecord(schema);
	}
}
