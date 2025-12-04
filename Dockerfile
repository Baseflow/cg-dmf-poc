FROM gradle:9.2.1-jdk21 AS build

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src ./src

RUN gradle clean build --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

