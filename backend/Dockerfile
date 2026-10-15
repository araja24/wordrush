# Build context is the repo root. One image serves both the API and the React app.

# Stage 1: build the frontend (Vite) into static files.
FROM node:24-slim AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# Stage 2: build the boot jar with the JDK, with the frontend bundled in as static resources.
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
COPY --from=frontend /frontend/dist backend/src/main/resources/static
RUN ./gradlew :backend:bootJar --no-daemon -x test

# Stage 3: run on a slim JRE as a non-root user.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 wordrush
COPY --from=build /workspace/backend/build/libs/backend-*.jar app.jar
USER wordrush
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
