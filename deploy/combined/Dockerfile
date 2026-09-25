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

# ---- rclone fetch stage ----
# Pinned to a specific current release rather than the distro package -
# Ubuntu 26.04's own `rclone` apt package is 1.60.1 (2022-era), several
# years behind; downloaded directly from rclone's own release server
# instead. Isolated in its own stage so curl/unzip/apt cache never end up
# in the runtime image - only the extracted binary is copied over.
FROM eclipse-temurin:25-jre AS rclone-fetch
ARG RCLONE_VERSION=1.75.1
RUN apt-get update -qq \
    && apt-get install -y -qq --no-install-recommends curl unzip \
    && curl -fsSL -o /tmp/rclone.zip \
       "https://downloads.rclone.org/v${RCLONE_VERSION}/rclone-v${RCLONE_VERSION}-linux-amd64.zip" \
    && unzip -q /tmp/rclone.zip -d /tmp \
    && install -m 755 "/tmp/rclone-v${RCLONE_VERSION}-linux-amd64/rclone" /usr/local/bin/rclone

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 spring
COPY --from=backend-build /app/app/build/libs/*.jar app.jar
# ionice is already present in this base image (util-linux) - only rclone
# itself needs adding. Both are invoked as one-shot subprocesses by
# RcloneDriveDownloadAdapter/RcloneDriveFolderListingAdapter, not run as a
# service.
COPY --from=rclone-fetch /usr/local/bin/rclone /usr/local/bin/rclone
# Headless Chromium for GumroadClaimAdapter, installed at build time (as
# root, with its system libraries) by the Playwright CLI bundled in the app
# jar itself - so the browser always matches the Playwright version the app
# was built with. Runtime downloads are then disabled; the non-root user
# couldn't write there anyway. Trade-off: this layer rebuilds whenever
# app.jar changes.
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN java -cp app.jar -Dloader.main=com.microsoft.playwright.CLI \
        org.springframework.boot.loader.launch.PropertiesLauncher \
        install --with-deps --only-shell chromium \
    && rm -rf /var/lib/apt/lists/*
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
