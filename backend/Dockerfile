# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------------
# Built in CI (GitHub Actions), never on the production server. See
# .github/workflows/deploy.yml and DEPLOY.md.
FROM gradle:8.7-jdk21 AS build
WORKDIR /app

# Resolve dependencies in their own layer, before any source is copied. A
# source-only change then reuses this layer instead of re-downloading Spring
# Boot, the AWS SDK and Razorpay on every build. Failures here are not fatal —
# the layer is a cache warm-up, and the real build below would fail anyway.
COPY build.gradle settings.gradle ./
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src

# `bootJar`, not `build`: `build` also produces a 133 KB *-plain.jar alongside
# the 73 MB executable jar, and the COPY below globs *.jar — which one won
# depended on glob ordering. `bootJar` emits exactly one jar. `-x test` keeps
# the existing behaviour of not running tests during the image build.
RUN gradle --no-daemon bootJar -x test

# ---- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
