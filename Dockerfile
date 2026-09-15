# syntax=docker/dockerfile:1
# JLoco-Login image, built from source: `check` (formatting, Error Prone, unit tests) then the
# application distribution, on a JRE-only runtime running as a non-root user.
#
# The integration tests need Docker (Testcontainers) and run in CI instead: ./gradlew integrationTest

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle sh ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle sh ./gradlew --no-daemon --quiet check installDist \
    && mkdir /app-jar && mv build/install/login/lib/login.jar /app-jar/

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S login && adduser -S -G login -h /app login \
    && mkdir -p /app/logs && chown login:login /app/logs
WORKDIR /app
# Libraries first: they change less often than the application jar.
COPY --from=build /src/build/install/login/lib/ /app/lib/
COPY --from=build /src/build/install/login/bin/login /app/bin/login
COPY --from=build /app-jar/login.jar /app/lib/login.jar
COPY docker.config.properties /app/login.config.properties

ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75" \
    LOGIN_LOG_DIR=/app/logs
USER login
EXPOSE 450 666
# Listening sockets are checked without connecting (a connection would be a login attempt).
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD netstat -ltn | grep -q ':450 ' && netstat -ltn | grep -q ':666 ' || exit 1
ENTRYPOINT ["/app/bin/login"]
