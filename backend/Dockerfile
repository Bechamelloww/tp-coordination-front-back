# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:25-jre
WORKDIR /app
# Agent Java OpenTelemetry : un fichier de plus, chargé par JAVA_TOOL_OPTIONS (compose.yaml).
# Version et empreinte épinglées : le build échoue si le fichier publié change.
ARG OTEL_REPO=github.com/open-telemetry/opentelemetry-java-instrumentation
ARG OTEL_VERSION=2.33.1
ARG AGENT=opentelemetry-javaagent.jar
# Dans /app, dossier qui existe déjà : --chmod=644 ne touche que le fichier, lisible par l'utilisateur 10001.
ADD --chmod=644 --checksum=sha256:e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149 \
    https://${OTEL_REPO}/releases/download/v${OTEL_VERSION}/${AGENT} \
    /app/${AGENT}
COPY --from=build /src/target/medislot.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
