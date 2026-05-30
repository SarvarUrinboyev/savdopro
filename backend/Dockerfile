# syntax=docker/dockerfile:1
# =====================================================================
#  SavdoPRO shop backend — multi-stage container build.
#
#  Build context is the REPO ROOT (see the root docker-compose.yml) because
#  the React SPA lives in ../frontend and is baked into the jar's static/
#  folder, so one jar serves both the UI and the /api.
#
#  Build:  docker build -f backend/Dockerfile -t savdopro/backend:latest .
#  Run:    use the root docker-compose.yml — it pairs us with Postgres +
#          the license server and supplies the shared JWT secret.
# =====================================================================

# ---------- Stage 1: build the React SPA ----------
FROM node:20-alpine AS frontend
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
# Optional hosted-web origins. Leave empty for a single-origin deploy (the
# backend serves the SPA + same-origin /api). For a split-origin hosted SaaS
# set VITE_LICENSE_URL (and VITE_API_URL) so the SPA reaches the right hosts.
ARG VITE_TARGET=""
ARG VITE_API_URL=""
ARG VITE_LICENSE_URL=""
ENV VITE_TARGET=$VITE_TARGET VITE_API_URL=$VITE_API_URL VITE_LICENSE_URL=$VITE_LICENSE_URL
RUN npx vite build --outDir /spa --emptyOutDir

# ---------- Stage 2: build the Spring Boot jar (SPA baked into static/) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/ ./
COPY --from=frontend /spa ./src/main/resources/static
RUN mvn -B -DskipTests clean package

# ---------- Stage 3: slim runtime ----------
# Ubuntu-based JRE (not Alpine): the report/receipt PDF pipeline relies on
# glibc + fontconfig, which musl/Alpine lacks. curl is added for the probe.
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 appuser
WORKDIR /app
COPY --from=build /app/target/barakat-market.jar app.jar
USER appuser
EXPOSE 8086
# DB-aware probe (rolls datasource health into UP/DOWN).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8086/actuator/health || exit 1
# Claim 75% of the container memory limit instead of guessing; die fast on OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
