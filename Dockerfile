FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY billing-service-api/pom.xml billing-service-api/pom.xml

WORKDIR /workspace/billing-service-api
RUN mvn -B -DskipTests dependency:go-offline

WORKDIR /workspace
COPY billing-service-api/src billing-service-api/src

WORKDIR /workspace/billing-service-api
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --from=build /workspace/billing-service-api/target/billing-service-api-*.jar app.jar

ENV SERVER_PORT=8080
ENV JAVA_OPTS=""

EXPOSE 8080

USER app

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
