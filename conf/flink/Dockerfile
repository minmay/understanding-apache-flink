# minikube image build -t minmay/flink:1.18.1-scala_2.12-hadoop-3.3.6-java11 ./conf/flink

FROM apache/hadoop:3.3.6 AS hadoop

FROM flink:1.18.1-scala_2.12-java11

ENV HADOOP_HOME /opt/hadoop
ENV FLINK_VERSION=1.18.1
ENV PARQUET_VERSION=1.13.1

COPY --from=hadoop --chown=flink:flink /opt/hadoop $HADOOP_HOME/
RUN rm $HADOOP_HOME/share/hadoop/common/lib/slf4j-reload4j-1.7.36.jar

