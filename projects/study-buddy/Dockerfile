# syntax=docker/dockerfile:1

# ---- Build stage --------------------------------------------------------
# Uses the Temurin Maven image matching this project's JDK (21) and builds
# with the repo's own pom.xml — no separate build script to keep in sync.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependency resolution in its own layer: only re-runs when pom.xml
# changes, not on every source edit.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage --------------------------------------------------------
# JRE (not JDK) — smaller image, no compiler needed to run a built jar.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Run as a non-root user.
RUN groupadd -r studybuddy && useradd -r -g studybuddy studybuddy \
    && mkdir -p /app/data/audio-recordings \
    && chown -R studybuddy:studybuddy /app

COPY --from=build /build/target/study-buddy-*.jar /app/app.jar

USER studybuddy

# The ONNX embedding model does a restricted System::load call at startup
# (see onnxruntime warning in logs) — enabling native access removes the
# JVM warning without changing behavior.
ENV JAVA_OPTS=""
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java --enable-native-access=ALL-UNNAMED $JAVA_OPTS -jar /app/app.jar"]
