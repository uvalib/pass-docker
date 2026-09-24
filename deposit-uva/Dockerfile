# syntax=docker/dockerfile:1.4
#
# Clones eclipse-pass/pass-support and pass-core at a git ref,
# overlays the DSpace files required for LibraOpen,
# and runs pass-deposit-core's Maven package recipe.
# The runtime stage is pass-deposit-services/deposit-core/Dockerfile
# (COPY --from=builder instead of a host Maven target/ and GHCR image).
#
# PASS_SUPPORT_REF and PASS_CORE_REF are git branches or tags to clone
# (use main for SNAPSHOT; 2.6.0-SNAPSHOT is a Maven version, not a git ref).

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
    test -n "${PASS_SUPPORT_REF}" || { echo "PASS_SUPPORT_REF is required (git branch or tag, e.g. main or 2.5.1)"; exit 1; }; \
    test -n "${PASS_CORE_REF}" || { echo "PASS_CORE_REF is required (git branch or tag, e.g. main or 2.5.1)"; exit 1; }; \
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
        /out/pass-deposit-service-exec.jar \
    && cp pass-deposit-services/deposit-core/entrypoint.sh /out/entrypoint.sh

# from deposit-core/Dockerfile (COPY --from=builder).
FROM eclipse-temurin:17.0.18_8-jre-noble

WORKDIR /app

COPY --from=builder /out/pass-deposit-service-exec.jar pass-deposit-service-exec.jar
COPY --from=builder /out/entrypoint.sh .

RUN apt update \
    && apt --no-install-recommends install -y curl \
    && chmod +x entrypoint.sh \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 1432 passdsgroup \
    && useradd -m -u 1532 -g passdsgroup passdsuser \
    && chown -R passdsuser:passdsgroup /app

USER passdsuser

ENTRYPOINT ["./entrypoint.sh"]
