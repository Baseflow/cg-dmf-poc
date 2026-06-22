FROM node:22-slim AS npm-install

WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --omit=dev

FROM gradle:9.5.1-jdk21 AS build

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src ./src
COPY frontend/package.json frontend/package-lock.json ./frontend/
COPY frontend/swagger-ui ./frontend/swagger-ui
COPY frontend/docs-viewer ./frontend/docs-viewer
COPY docs ./docs
COPY --from=npm-install /app/node_modules ./frontend/node_modules

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
