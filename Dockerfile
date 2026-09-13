# StarLoco-Login image, built from source: tests + jar in a Gradle stage, JRE-only runtime.
FROM gradle:8.10.2-jdk8 AS build
WORKDIR /src
COPY build.gradle settings.gradle ./
COPY libs ./libs
COPY src ./src
COPY test ./test
RUN gradle --no-daemon --quiet test jar

FROM alpine:latest

RUN apk add --no-cache openjdk11-jre

COPY --from=build /src/build/libs/login.jar /app/login.jar
COPY docker.config.properties /app/login.config.properties

WORKDIR /app

CMD ["java", "-jar", "login.jar"]
