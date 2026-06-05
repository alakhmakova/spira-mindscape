# syntax=docker/dockerfile:1.7
# Single-origin image for Cloud Run: builds the SPA, embeds it in the Spring Boot
# jar (served from classpath:/static/), so one container serves both the API and
# the frontend on one origin. See docs/deploy-gcp-cloud-run.md.

# ---- Stage 1: build the SPA ----
FROM node:20-alpine AS frontend
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build          # → /app/dist

# ---- Stage 2: build the backend jar (with the SPA inside it) ----
# Base image already has Maven, so we call `mvn` directly (the ./mvnw wrapper would
# need `unzip`, which isn't in the image).
FROM maven:3.9.9-eclipse-temurin-17 AS backend
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q -B -DskipTests dependency:go-offline
COPY backend/src src
# Place the built SPA so Spring serves it from classpath:/static/ (single origin).
COPY --from=frontend /app/dist/ src/main/resources/static/
RUN mvn -q -B -DskipTests clean package

# ---- Stage 3: slim runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
# Cloud Run injects $PORT (default 8080); application.properties reads ${PORT:8080}.
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
