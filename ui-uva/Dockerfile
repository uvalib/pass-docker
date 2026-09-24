# syntax=docker/dockerfile:1.4
#
# Clones uvalib/pass-ui at a git ref and runs the pnpm install+build steps
# from pass-ui/build.sh. The runtime stage is pass-ui/Dockerfile, with COPY
# paths pointed at the builder (the original file expects dist/ already in
# the build context from a host `pnpm build`).
#
# PASS_UI_REF is a required git branch or tag (e.g. uvalib). Ember config
# is baked at `pnpm build` from the PASS_UI_* args (same vars as pass-docker .env).

FROM node:24-bookworm AS builder

ARG PASS_UI_REPO=https://github.com/uvalib/pass-ui.git
ARG PASS_UI_REF
ARG PASS_UI_ROOT_URL=/app
ARG STATIC_CONFIG_URL=/config.json
ARG SCHEMA_SERVICE_PATH=/schema
ARG DOI_SERVICE_PATH=/doi/journal
ARG MANUSCRIPT_SERVICE_LOOKUP_PATH=/doi/manuscript
ARG POLICY_SERVICE_POLICY_PATH=/policy/policies
ARG POLICY_SERVICE_REPOSITORY_PATH=/policy/repositories
ARG PASS_API_NAMESPACE=data

ENV HUSKY=0 \
    CI=true \
    PASS_UI_ROOT_URL=${PASS_UI_ROOT_URL} \
    STATIC_CONFIG_URL=${STATIC_CONFIG_URL} \
    SCHEMA_SERVICE_PATH=${SCHEMA_SERVICE_PATH} \
    DOI_SERVICE_PATH=${DOI_SERVICE_PATH} \
    MANUSCRIPT_SERVICE_LOOKUP_PATH=${MANUSCRIPT_SERVICE_LOOKUP_PATH} \
    MANUSCRIPT_SERVICE_LOOKUP_PUBLIC_PATH=${MANUSCRIPT_SERVICE_LOOKUP_PATH} \
    POLICY_SERVICE_POLICY_PATH=${POLICY_SERVICE_POLICY_PATH} \
    POLICY_SERVICE_REPOSITORY_PATH=${POLICY_SERVICE_REPOSITORY_PATH} \
    PASS_API_NAMESPACE=${PASS_API_NAMESPACE}

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN corepack enable && corepack prepare pnpm@9 --activate

RUN set -eux; \
    test -n "${PASS_UI_REF}" || { echo "PASS_UI_REF is required (git branch or tag, e.g. uvalib)"; exit 1; }; \
    git clone --depth 1 --branch "${PASS_UI_REF}" "${PASS_UI_REPO}" /src

WORKDIR /src

RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

RUN pnpm build

# create_sbom like ci does
RUN pnpm run create_sbom

# From pass-ui/Dockerfile (COPY --from=builder).
FROM nginxinc/nginx-unprivileged:1.27.3-alpine

ARG EMBER_ROOT_URL
ARG USER_SERVICE_URL
ARG SCHEMA_SERVICE_URL
ARG DOI_SERVICE_URL
ARG POLICY_SERVICE_URL

ENV USER_SERVICE_URL=${USER_SERVICE_URL:-/pass-user-service/whoami} \
    SCHEMA_SERVICE_URL=${SCHEMA_SERVICE_URL:-/schemaservice} \
    DOI_SERVICE_URL=${DOI_SERVICE_URL:-/doiservice/journal} \
    POLICY_SERVICE_URL=${POLICY_SERVICE_URL:-/policies} \
    PASS_UI_ROOT_URL=${EMBER_ROOT_URL:-/app/} \
    METADATA_SCHEMA_URI=${METADATA_SCHEMA_URI:-https://oa-pass.github.com/metadata-schemas/jhu/global.json} \
    MANUSCRIPT_SERVICE_LOOKUP_URL=${MANUSCRIPT_SERVICE_LOOKUP_URL:-/downloadservice/lookup} \
    MANUSCRIPT_SERVICE_DOWNLOAD_URL=${MANUSCRIPT_SERVICE_DOWNLOAD_URL:-/downloadservice/download} \
    PASS_API_URL=${PASS_API_URL:-https://localhost} \
    PASS_API_NAMESPACE=${PASS_API_NAMESPACE:-/api/v1} \
    STATIC_CONFIG_URL=${STATIC_CONFIG_URL:-/app/config.json} \
    PASS_UI_PORT=80

USER root

COPY --from=builder /src/.docker/bin/entrypoint.sh /bin/
COPY --from=builder /src/.docker/nginx-template.conf /
COPY --from=builder /src/pass-ui-*-cyclonedx-sbom.json /
COPY --from=builder /src/dist/ /usr/share/nginx/html/app/

RUN apk --no-cache add gettext && \
    chmod a+x /bin/entrypoint.sh && \
    chown nginx:nginx /bin/entrypoint.sh && \
    chown nginx:nginx /nginx-template.conf && \
    chown -R nginx:nginx /usr/share/nginx/html/app

USER nginx

ENTRYPOINT [ "/bin/entrypoint.sh" ]
