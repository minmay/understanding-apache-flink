FLINK_VERSION = 1.18.1
SCALA_VERSION = 2.12
KAFKA_VERSION = 2.5.1
HADOOP_VERSION = 2.10.2
PARQUET_VERSION = 1.13.1
HIVE_VERSION = 3.1.3

HADOOP = opt/hadoop/hadoop-$(HADOOP_VERSION)/bin/hadoop
FLINK_CLUSTER = opt/flink/flink-$(FLINK_VERSION)/bin/start-cluster.sh

S3_LIB = opt/flink/flink-$(FLINK_VERSION)/plugins/flink-s3-fs-hadoop/flink-s3-fs-hadoop-$(FLINK_VERSION).jar
KAFKA_CONNECTOR_LIB = opt/lib/connector/flink-sql-connector-kafka-3.1.0-1.18.jar
PARQUET_LIB = opt/lib/parquet/flink-parquet-$(FLINK_VERSION).jar opt/lib/parquet/parquet-column-$(PARQUET_VERSION).jar opt/lib/parquet/parquet-hadoop-$(PARQUET_VERSION).jar opt/lib/parquet/parquet-common-$(PARQUET_VERSION).jar opt/lib/parquet/parquet-format-structures-$(PARQUET_VERSION).jar opt/lib/parquet/parquet-jackson-$(PARQUET_VERSION).jar opt/lib/parquet/parquet-encoding-$(PARQUET_VERSION).jar
JDBC_LIB = opt/lib/connector/flink-connector-jdbc-3.1.2-1.18.jar

hadoop_classpath = `./opt/hadoop/hadoop-$(HADOOP_VERSION)/bin/hadoop classpath`

echo-hadoop-classpath :
	echo $(hadoop_classpath)

$(HADOOP) :
	curl https://dlcdn.apache.org/hadoop/common/hadoop-$(HADOOP_VERSION)/hadoop-$(HADOOP_VERSION).tar.gz -L -O
	mkdir -p opt/hadoop
	tar xvfz hadoop-$(HADOOP_VERSION).tar.gz -C opt/hadoop
	rm opt/hadoop/hadoop-$(HADOOP_VERSION)/share/hadoop/common/lib/slf4j-reload4j-1.7.36.jar
	rm hadoop-$(HADOOP_VERSION).tar.gz

$(FLINK_CLUSTER) :
	curl https://archive.apache.org/dist/flink/flink-$(FLINK_VERSION)/flink-$(FLINK_VERSION)-bin-scala_$(SCALA_VERSION).tgz -L -O
	mkdir -p opt/flink
	tar xvfz flink-$(FLINK_VERSION)-bin-scala_$(SCALA_VERSION).tgz -C opt/flink
	rm flink-$(FLINK_VERSION)-bin-scala_$(SCALA_VERSION).tgz

$(JDBC_LIB) : $(FLINK_CLUSTER)
	curl https://repo1.maven.org/maven2/org/apache/flink/flink-connector-jdbc/3.1.2-1.18/flink-connector-jdbc-3.1.2-1.18.jar -L -O
	mkdir -p opt/lib/connector
	mv flink-connector-jdbc-3.1.2-1.18.jar opt/lib/connector

$(S3_LIB) : $(FLINK_CLUSTER)
	mkdir opt/flink/flink-$(FLINK_VERSION)/plugins/flink-s3-fs-hadoop
	cp opt/flink/flink-$(FLINK_VERSION)/opt/flink-s3-fs-hadoop-$(FLINK_VERSION).jar opt/flink/flink-$(FLINK_VERSION)/plugins/flink-s3-fs-hadoop

$(KAFKA_CONNECTOR_LIB) :
	mkdir -p opt/lib/connector
	curl https://repo1.maven.org/maven2/org/apache/flink/flink-sql-connector-kafka/3.1.0-1.18/flink-sql-connector-kafka-3.1.0-1.18.jar -L -O
	mv flink-sql-connector-kafka-3.1.0-1.18.jar opt/lib/connector/

opt/kafka/kafka_$(SCALA_VERSION)-$(KAFKA_VERSION)/bin/kafka-topics.sh :
	curl https://archive.apache.org/dist/kafka/$(KAFKA_VERSION)/kafka_$(SCALA_VERSION)-$(KAFKA_VERSION).tgz -L -O
	mkdir -p opt/kafka
	tar xvfz kafka_$(SCALA_VERSION)-$(KAFKA_VERSION).tgz -C opt/kafka
	rm kafka_$(SCALA_VERSION)-$(KAFKA_VERSION).tgz

download-hive :
	curl https://dlcdn.apache.org/hive/hive-$(HIVE_VERSION)/apache-hive-$(HIVE_VERSION)-bin.tar.gz -L -O
	mkdir -p opt/hive
	tar xvfz apache-hive-$(HIVE_VERSION)-bin.tar.gz -C opt/hive
	rm apache-hive-$(HIVE_VERSION)-bin.tar.gz

.PHONY: start-cluster
start-cluster : $(FLINK_CLUSTER) $(S3_LIB) $(HADOOP)
	AWS_ACCESS_KEY_ID=understanding-apache-flink AWS_SECRET_ACCESS_KEY=understanding-apache-flink HADOOP_CLASSPATH=$(hadoop_classpath) opt/flink/flink-$(FLINK_VERSION)/bin/start-cluster.sh

.PHONY: list-topics
list-topics :
	opt/kafka/kafka_$(SCALA_VERSION)-$(KAFKA_VERSION)/bin/kafka-topics.sh --zookeeper localhost:2181 --list

.PHONY: stop-cluster
stop-cluster : $(FLINK_CLUSTER)
	./opt/flink/flink-$(FLINK_VERSION)/bin/stop-cluster.sh

$(PARQUET_LIB) :
	mkdir -p opt/lib/parquet
	curl https://repo1.maven.org/maven2/org/apache/flink/flink-parquet/$(FLINK_VERSION)/flink-parquet-$(FLINK_VERSION).jar -O -L
	mv flink-parquet-$(FLINK_VERSION).jar opt/lib/parquet
	curl https://repo1.maven.org/maven2/org/apache/parquet/parquet-column/$(PARQUET_VERSION)/parquet-column-$(PARQUET_VERSION).jar -O -L
	mv parquet-column-$(PARQUET_VERSION).jar opt/lib/parquet
	curl https://repo1.maven.org/maven2/org/apache/parquet/parquet-hadoop/$(PARQUET_VERSION)/parquet-hadoop-$(PARQUET_VERSION).jar -O -L
	mv parquet-hadoop-$(PARQUET_VERSION).jar opt/lib/parquet
	curl https://repo1.maven.org/maven2/org/apache/parquet/parquet-common/$(PARQUET_VERSION)/parquet-common-$(PARQUET_VERSION).jar -O -L
	mv parquet-common-$(PARQUET_VERSION).jar opt/lib/parquet
	curl https://repo1.maven.org/maven2/org/apache/parquet/parquet-format-structures/$(PARQUET_VERSION)/parquet-format-structures-$(PARQUET_VERSION).jar -O -L
	mv parquet-format-structures-$(PARQUET_VERSION).jar opt/lib/parquet
	curl https://repo1.maven.org/maven2/org/apache/parquet/parquet-jackson/$(PARQUET_VERSION)/parquet-jackson-$(PARQUET_VERSION).jar -O -L
	mv parquet-jackson-$(PARQUET_VERSION).jar opt/lib/parquet
	curl https://repo1.maven.org/maven2/org/apache/parquet/parquet-encoding/$(PARQUET_VERSION)/parquet-encoding-$(PARQUET_VERSION).jar -O -L
	mv parquet-encoding-$(PARQUET_VERSION).jar opt/lib/parquet

.PHONY: sql-client
sql-client : $(FLINK_CLUSTER) $(S3_LIB) $(KAFKA_CONNECTOR_LIB) $(JDBC_LIB) $(HADOOP) $(PARQUET_LIB)
	HADOOP_CLASSPATH=$(hadoop_classpath) ./opt/flink/flink-$(FLINK_VERSION)/bin/sql-client.sh \
		--jar ./opt/lib/connector/flink-sql-connector-kafka-3.1.0-1.18.jar \
		--jar ./opt/lib/parquet/flink-parquet-$(FLINK_VERSION).jar \
		--jar ./opt/lib/parquet/parquet-column-$(PARQUET_VERSION).jar \
		--jar ./opt/lib/parquet/parquet-hadoop-$(PARQUET_VERSION).jar \
		--jar ./opt/lib/parquet/parquet-common-$(PARQUET_VERSION).jar \
		--jar ./opt/lib/parquet/parquet-format-structures-$(PARQUET_VERSION).jar \
		--jar ./opt/lib/parquet/parquet-jackson-$(PARQUET_VERSION).jar \
		--jar ./opt/lib/parquet/parquet-encoding-$(PARQUET_VERSION).jar

.PHONY: open-ui
open-ui :
	open http://localhost:8081

build/data-gen-agent-js/package.json :
	git clone https://github.com/minmay/data-gen-agent-js.git build/data-gen-agent-js

.PHONY: image-build
image-build : build/data-gen-agent-js/package.json
	minikube image build -t minmay/data-gen-agent-js:1.0.0 build/data-gen-agent-js
	minikube image build -t minmay/understanding-apache-flink:latest .
	minikube image build -t minmay/flink:1.18.1-scala_2.12-hadoop-3.3.6-java11 ./conf/flink

.PHONY: deploy-k8s
deploy-k8s  :
	kubectl create namespace understanding-apache-flink
	kubectl create -f https://github.com/jetstack/cert-manager/releases/download/v1.8.2/cert-manager.yaml
	helm repo add flink-operator-repo https://downloads.apache.org/flink/flink-kubernetes-operator-1.8.0/ -n understanding-apache-flink
	helm install --set webhook.create=false flink-kubernetes-operator flink-operator-repo/flink-kubernetes-operator -n understanding-apache-flink
	kubectl create -f 'https://strimzi.io/install/latest?namespace=understanding-apache-flink' -n understanding-apache-flink
	kubectl apply -f conf/deployment-understanding-apache-flink.yaml -n understanding-apache-flink
	kubectl apply -f conf/services-understanding-apache-flink.yaml -n understanding-apache-flink
	kubectl apply -f conf/flink-operator-understanding-apache-flink.yaml -n understanding-apache-flink

.PHONY: tear-down-k8s
tear-down-k8s :
	kubectl delete -f conf/flink-operator-understanding-apache-flink.yaml -n understanding-apache-flink
	kubectl delete -f conf/services-understanding-apache-flink.yaml -n understanding-apache-flink
	kubectl delete -f conf/deployment-understanding-apache-flink.yaml -n understanding-apache-flink
	helm uninstall flink-kubernetes-operator -n understanding-apache-flink
	helm repo remove flink-operator-repo -n understanding-apache-flink
	kubectl delete -f 'https://strimzi.io/install/latest?understanding-apache-flink' -n understanding-apache-flink
	kubectl delete -f https://github.com/jetstack/cert-manager/releases/download/v1.8.2/cert-manager.yaml
	kubectl delete namespace understanding-apache-flink
