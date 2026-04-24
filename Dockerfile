FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app
COPY . /app
RUN ./gradlew --no-daemon bootJar -x test
RUN set -e; JAR=$(find build/libs -maxdepth 1 -name "*.jar" ! -name "*-plain.jar" | head -1); cp "$JAR" /app/app.jar

FROM eclipse-temurin:21-jre-jammy

RUN groupadd -r app -g 1000 && useradd -r -u 1000 -g app app
WORKDIR /app
COPY --from=builder /app/app.jar ./app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
