# Build context is the repo root (needs the Gradle wrapper and settings).
# Stage 1: build the boot jar with the JDK.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY game-core/build.gradle.kts game-core/
COPY backend/build.gradle.kts backend/
COPY bot/build.gradle.kts bot/
# Resolve dependencies in their own layer so code changes don't re-download them.
RUN ./gradlew :backend:dependencies --no-daemon > /dev/null
COPY game-core/src game-core/src
COPY backend/src backend/src
RUN ./gradlew :backend:bootJar --no-daemon -x test

# Stage 2: run on a slim JRE as a non-root user.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 wordrush
COPY --from=build /workspace/backend/build/libs/backend-*.jar app.jar
USER wordrush
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
