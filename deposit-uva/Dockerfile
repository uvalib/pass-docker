# syntax=docker/dockerfile:1.4
#
# Clones eclipse-pass/pass-support at a git tag, 
# overlays the DSpace files required for LibraOpen,
# and runs pass-deposit-core's Maven package recipe.
# The final stage reuses the published 
# deposit-services-core image and replaces the jar file.
#
# PASS_VERSION selects the upstream GHCR image tag: ghcr.io/eclipse-pass/deposit-services-core:${PASS_VERSION}.
# PASS_SUPPORT_REF and PASS_CORE_REF are required git tags (e.g. 2.5.1).

ARG PASS_VERSION=2.5.1

FROM maven:3.9-eclipse-temurin-17 AS builder

ARG PASS_SUPPORT_REF
ARG PASS_CORE_REF
ARG PASS_SUPPORT_REPO=https://github.com/eclipse-pass/pass-support.git
ARG PASS_CORE_REPO=https://github.com/eclipse-pass/pass-core.git

RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

# clone pass-support and pass-core 
RUN set -eux; \
    test -n "${PASS_SUPPORT_REF}" || { echo "PASS_SUPPORT_REF is required (git tag, e.g. 2.5.1)"; exit 1; }; \
    test -n "${PASS_CORE_REF}" || { echo "PASS_CORE_REF is required (git tag, e.g. 2.5.1)"; exit 1; }; \
    git clone --depth 1 --branch "${PASS_SUPPORT_REF}" "${PASS_SUPPORT_REPO}" /src/pass-support; \
    git clone --depth 1 --branch "${PASS_CORE_REF}" "${PASS_CORE_REPO}" /src/pass-core

COPY overlay/DSpaceMetadataMapper.java \
    /src/pass-support/pass-deposit-services/deposit-core/src/main/java/org/eclipse/pass/deposit/provider/dspace/DSpaceMetadataMapper.java
COPY overlay/DSpaceMetadataMapperTest.java \
    /src/pass-support/pass-deposit-services/deposit-core/src/test/java/org/eclipse/pass/deposit/provider/dspace/DSpaceMetadataMapperTest.java

WORKDIR /src/pass-support

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f /src/pass-core/pom.xml -pl pass-core-test-config -am install -DskipTests \
    && mvn -pl pass-deposit-services/deposit-core -am package \
        -Dtest=DSpaceMetadataMapperTest \
        -Dsurefire.failIfNoSpecifiedTests=false \
    && mkdir -p /out \
    && cp pass-deposit-services/deposit-core/target/deposit-core-*-exec.jar \
        /out/pass-deposit-service-exec.jar

# replace the default deposit-service jar with the one built from this repo
FROM ghcr.io/eclipse-pass/deposit-services-core:${PASS_VERSION}
USER root
COPY --from=builder /out/pass-deposit-service-exec.jar /app/pass-deposit-service-exec.jar
RUN chown passdsuser:passdsgroup /app/pass-deposit-service-exec.jar
USER passdsuser
