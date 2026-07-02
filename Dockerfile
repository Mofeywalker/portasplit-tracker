# syntax=docker/dockerfile:1

# ---- Build stage: compile the React frontend + Spring Boot fat jar ----
# The frontend-maven-plugin (bound to generate-resources) downloads Node, runs `npm install`
# and `npm run build`, emitting the SPA into target/classes/static so it is packaged into the jar.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Build (frontend + backend). Tests are skipped for image builds. Maven downloads
# dependencies and the frontend-maven-plugin downloads Node on first run.
COPY pom.xml .
COPY frontend ./frontend
COPY src ./src
RUN mvn -B -DskipTests clean package

# ---- Runtime stage: just a JRE + the fat jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl for the compose healthcheck against /actuator/health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# SQLite database lives here; mounted as a volume in docker-compose so it survives restarts.
RUN mkdir -p /app/data
ENV PORTASPLIT_DB_PATH=/app/data/portasplit.db

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
# Cap heap relative to the container limit instead of the host's total RAM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
