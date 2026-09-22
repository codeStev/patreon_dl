# syntax=docker/dockerfile:1

# Combined image: Spring Boot serves the built frontend statically via its
# embedded Tomcat, instead of running frontend/backend as two containers.
# Build context is the repo root.

# ---- Frontend build stage ----
FROM node:26-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci || npm install
COPY frontend/ .
RUN npm run build

# ---- Backend build stage ----
FROM eclipse-temurin:25-jdk AS backend-build
WORKDIR /app

# Cache Gradle dependency resolution before copying full sources - only the
# COPY below invalidates when source code (not build files) changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY ingest-core/build.gradle.kts ingest-core/build.gradle.kts
COPY ingest-providers-default/build.gradle.kts ingest-providers-default/build.gradle.kts
COPY app/build.gradle.kts app/build.gradle.kts
RUN ./gradlew --no-daemon :app:dependencies || true

COPY ingest-core/src ingest-core/src
COPY ingest-providers-default/src ingest-providers-default/src
COPY app/src app/src

# The frontend build lands on the backend's static classpath root before
# packaging, so Spring Boot's default static-resource handling serves it
# with no extra config.
COPY --from=frontend-build /app/frontend/dist app/src/main/resources/static

RUN ./gradlew --no-daemon clean :app:bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 spring
COPY --from=backend-build /app/app/build/libs/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
