# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy@sha256:29467857e8bde40ab1f7befecbda0ea764b95afec1cc7f89aa90f7a766577e19 AS build

WORKDIR /workspace
COPY --chmod=0755 gradlew ./gradlew
COPY gradle ./gradle
COPY build.gradle settings.gradle gradle.properties gradle.lockfile ./
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew resolveRuntimeDependencies --no-configuration-cache --no-daemon

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew clean installDist --offline --no-daemon

FROM gcr.io/distroless/java17-debian13:nonroot@sha256:1c6329f129ec1680322029528b12b5798e770658bee24d3a4854157992157255

ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="Blazra" \
      org.opencontainers.image.description="A least-privilege Kubernetes image update sidecar" \
      org.opencontainers.image.source="https://github.com/ocularminds/blazra" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"

WORKDIR /opt/blazra
COPY --from=build --chown=65532:65532 /workspace/build/install/blazra/lib ./lib

USER 65532:65532
ENTRYPOINT ["java", "-cp", "/opt/blazra/lib/*", "io.github.ocularminds.blazra.BlazraApplication"]
