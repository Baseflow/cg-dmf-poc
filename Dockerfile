FROM gradle:9.2.1-jdk21 AS build

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src ./src

RUN gradle clean installDist --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=build /app/build/install/DMF-PoC ./app

EXPOSE 8080

ENTRYPOINT ["./app/bin/DMF-PoC"]

