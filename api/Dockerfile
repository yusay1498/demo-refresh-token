FROM amazoncorretto:25

RUN mkdir -p /opt/newrelic \
	&& curl --fail --silent --show-error --location \
		--output /opt/newrelic/newrelic.jar \
		https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic.jar \
	&& curl --fail --silent --show-error --location \
		--output /opt/newrelic/newrelic.yml \
		https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic.yml

ENV NEW_RELIC_APP_NAME=api
ENV NEW_RELIC_DISTRIBUTED_TRACING_ENABLED=true
ENV NEW_RELIC_LOG_EXTENSION_ADD_MDC=true
ENV NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED=false

ARG JAR=target/*.jar

COPY $JAR /opt/app.jar

ENTRYPOINT ["java", "-javaagent:/opt/newrelic/newrelic.jar", "-jar", "/opt/app.jar"]
