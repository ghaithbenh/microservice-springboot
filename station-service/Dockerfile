# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy every module POM first so Maven can resolve the reactor.
COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY louage-service/pom.xml louage-service/pom.xml
COPY station-service/pom.xml station-service/pom.xml
COPY trip-service/pom.xml trip-service/pom.xml

COPY station-service/src station-service/src

RUN mvn -B -ntp -pl station-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --home-dir /app app

WORKDIR /app
RUN mkdir -p /app/data && chown -R app:app /app

COPY --from=build --chown=app:app /workspace/station-service/target/station-service-1.0.0.jar /app/app.jar

USER app
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
