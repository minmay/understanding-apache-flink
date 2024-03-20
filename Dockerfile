# minikube image build -t minmay/understanding-apache-flink:latest

ARG APP_DIR=/opt/understanding-apache-flink
FROM gradle:7.6.4-jdk17 as build
LABEL vendor="Marco A. Villalobos"
LABEL app="understanding-apache-flink"

ARG APP_DIR
ENV GRADLE_OPTS -Dorg.gradle.daemon=false
WORKDIR $APP_DIR

RUN mkdir -p $APP_DIR/src/main
COPY build.gradle settings.gradle $APP_DIR/
COPY src/main $APP_DIR/src/main
RUN gradle shadowJar --stacktrace





