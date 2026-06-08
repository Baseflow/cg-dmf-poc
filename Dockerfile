FROM gradle:9.5.1-jdk21 AS build

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src ./src
COPY frontend ./frontend

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle clean installDist --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=build /app/build/install/DMF-PoC ./app

# ZGC eliminates multi-second stop-the-world GC pauses under concurrent load.
# The Gradle installDist startup script picks up JAVA_OPTS from the environment.
ENV JAVA_OPTS="-XX:+UseZGC"

EXPOSE 8080

ENTRYPOINT ["./app/bin/DMF-PoC"]

