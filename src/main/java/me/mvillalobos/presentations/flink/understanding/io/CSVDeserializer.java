package me.mvillalobos.presentations.flink.understanding.io;

import me.mvillalobos.presentations.flink.understanding.domain.RawTimeSeries;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;

public class CSVDeserializer implements KafkaRecordDeserializationSchema<RawTimeSeries> {

	private final static Logger logger = LoggerFactory.getLogger(CSVDeserializer.class);

	@Override
	public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<RawTimeSeries> out) throws IOException {
		String contentType = null;
		String controlStreamType = null;
		for (Header header : record.headers()) {
			final String key = header.key();
			final byte[] value = header.value();
			switch (key) {
				case "Content-Type":
					contentType = new String(value);
					break;
				case "X-Control-Stream-Type":
					controlStreamType = new String(value);
					break;
			}
		}

		final String recordValue = new String(record.value());
		Instant eventTime = Instant.ofEpochMilli(record.timestamp());

		try (StringReader reader = new StringReader(recordValue)) {
			final CSVParser parser = CSVFormat.RFC4180.parse(reader);
			for (CSVRecord csvRecord : parser.getRecords()) {
				try {
					final Instant step = Instant.parse(csvRecord.get(0));
					final String name = csvRecord.get(1);
					final String value = csvRecord.get(2);
					final RawTimeSeries rawTimeSeries = new RawTimeSeries();
					rawTimeSeries.setContentType(contentType);
					rawTimeSeries.setControlStreamType(controlStreamType);
					rawTimeSeries.setStep(step);
					rawTimeSeries.setName(name);
					rawTimeSeries.setValue(value);
					rawTimeSeries.setEventTime(eventTime);
					logger.info("collect: {}", rawTimeSeries);
					out.collect(rawTimeSeries);
				} catch (Exception e) {
					logger.error("Skipping bad record: {}", csvRecord, e);
				}
			}
		}
		catch (Exception e) {
			logger.error("Skipping bad records: {}", recordValue, e);
		}
	}

	@Override
	public TypeInformation<RawTimeSeries> getProducedType() {
		return TypeInformation.of(RawTimeSeries.class);
	}
}
