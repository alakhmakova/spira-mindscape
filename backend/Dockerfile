# syntax=docker/dockerfile:1.7

FROM --platform=linux/arm64 maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests clean package

FROM --platform=linux/arm64 eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
