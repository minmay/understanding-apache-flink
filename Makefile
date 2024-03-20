all: download-flink conf-s3 download-kafka-sql download-kafka conf-kafka

flink_version = 1.18.1
scala_version = 2.12
kafka_version = 2.5.1
hadoop_version = 2.10.2
hive_version = 3.1.3
hadoop_classpath = `./opt/hadoop/hadoop-$(hadoop_version)/bin/hadoop classpath`

echo-hadoop-classpath:
	echo $(hadoop_classpath)

download-hadoop:
	curl https://dlcdn.apache.org/hadoop/common/hadoop-$(hadoop_version)/hadoop-$(hadoop_version).tar.gz -L -O
	mkdir -p opt/hadoop
	tar xvfz hadoop-$(hadoop_version).tar.gz -C opt/hadoop
	rm hadoop-$(hadoop_version).tar.gz

download-flink:
	curl https://archive.apache.org/dist/flink/flink-$(flink_version)/flink-$(flink_version)-bin-scala_$(scala_version).tgz -L -O
	mkdir -p opt/flink
	tar xvfz flink-$(flink_version)-bin-scala_$(scala_version).tgz -C opt/flink
	rm flink-$(flink_version)-bin-scala_$(scala_version).tgz

download-flink-jdbc:
	curl https://repo.maven.apache.org/maven2/org/apache/flink/flink-sql-jdbc-driver-bundle/$(flink_version)/flink-sql-jdbc-driver-bundle-$(flink_version).jar -L -O
	mkdir -p opt/flink/flink-$(flink_version)/jdbc/
	mv flink-sql-jdbc-driver-bundle-$(flink_version).jar opt/flink/flink-$(flink_version)/jdbc/

conf-s3: opt/flink/flink-$(flink_version)/opt/$(flink_version).jar
	mkdir opt/flink/flink-$(flink_version)/plugins/flink-s3-fs-hadoop
	cp opt/flink/flink-$(flink_version)/opt/flink-s3-fs-hadoop-$(flink_version).jar opt/flink/flink-$(flink_version)/plugins/flink-s3-fs-hadoop

download-kafka-sql:
	curl https://repo.maven.apache.org/maven2/org/apache/flink/flink-sql-connector-kafka_$(scala_version)/$(flink_version)/flink-sql-connector-kafka_$(scala_version)-$(flink_version).jar -L -O
	mv flink-sql-connector-kafka_$(scala_version)-$(flink_version).jar opt/flink/flink-$(flink_version)/opt

download-kafka:
	curl https://archive.apache.org/dist/kafka/$(kafka_version)/kafka_$(scala_version)-$(kafka_version).tgz -L -O
	mkdir -p opt/kafka
	tar xvfz kafka_$(scala_version)-$(kafka_version).tgz -C opt/kafka
	rm kafka_$(scala_version)-$(kafka_version).tgz

download-hive:
	curl https://dlcdn.apache.org/hive/hive-$(hive_version)/apache-hive-$(hive_version)-bin.tar.gz -L -O
	mkdir -p opt/hive
	tar xvfz apache-hive-$(hive_version)-bin.tar.gz -C opt/hive
	rm apache-hive-$(hive_version)-bin.tar.gz

start-cluster:
	AWS_ACCESS_KEY_ID=understanding-apache-flink AWS_SECRET_ACCESS_KEY=understanding-apache-flink HADOOP_CLASSPATH=$(hadoop_classpath) opt/flink/flink-$(flink_version)/bin/start-cluster.sh

docker-list-topics:
	opt/kafka/kafka_$(scala_version)-$(kafka_version)/bin/kafka-topics.sh --zookeeper localhost:2181 --list

stop-cluster:
	./opt/flink/flink-$(flink_version)/bin/stop-cluster.sh

sql-client:
	HADOOP_CLASSPATH=$(hadoop_classpath) ./opt/flink/flink-$(flink_version)/bin/sql-client.sh \
		--jar ./build/libs/parquet/flink-parquet-1.18.1.jar \
		--jar ./build/libs/parquet/parquet-column-1.13.1.jar \
		--jar ./build/libs/parquet/parquet-hadoop-1.13.1.jar \
		--jar ./build/libs/parquet/parquet-common-1.13.1.jar \
		--jar ./build/libs/parquet/parquet-format-structures-1.13.1.jar \
		--jar ./build/libs/parquet/parquet-jackson-1.13.1.jar \
		--jar ./build/libs/parquet/parquet-encoding-1.13.1.jar

open-ui:
	open http://localhost:8081

