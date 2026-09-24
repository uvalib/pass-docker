# PASS Docker @ uvalib

This branch adds a University of Virginia overlay on Eclipse PASS Docker so deposit-services can submit to **LibraOpen** (DSpace Publication collections) instead of stock DSpace traditional-item forms.

Upstream PASS docs: [PASS Docker](https://docs.eclipse-pass.org/developer-documentation/pass-docker).

## Why this build exists

Stock PASS `DSpaceMetadataMapper` makes API calls to DSpace workspace-item sections `traditionalpageone` and `traditionalpagetwo`.

LibraOpen Publication collections use the DSpace **Publication** submission process defined in DSpace `item-submission.xml`:

- Libra Open dev and production are built in terraform at terraform-infrastructure/dspace.library.virginia.edu/staging(production)/ansible/config/dspace9/item-submission.xml
- The copy of [dspace/item-submission.xml](dspace/item-submission.xml) in this repo is used for the docker build ( running dspace as an additional compose file here is currently untested)



## This branch adds:


| Path                                                                                                     | Purpose                                                                                                                                                    |
| -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `[docker-compose-uvalib.yml](docker-compose-uvalib.yml)`                                                 | Compose overlay: build local UVA deposit-services and pass-ui images instead of pulling GHCR                                                               |
| `[deposit-uva/Dockerfile](deposit-uva/Dockerfile)`                                                       | Clones `pass-support` at a git ref, overlays the UVA files, runs deposit-core's Maven package, copys the JAR into the original deposit-servcies-core image |
| `[deposit-uva/overlay/DSpaceMetadataMapper.java](deposit-uva/overlay/DSpaceMetadataMapper.java)`         | LibraOpen Publication JSON mapper                                                                                                                          |
| `[deposit-uva/overlay/DSpaceMetadataMapperTest.java](deposit-uva/overlay/DSpaceMetadataMapperTest.java)` | Unit tests run by during the image build                                                                                                                   |
| `[ui-uva/Dockerfile](ui-uva/Dockerfile)`                                                                 | Clones `uvalib/pass-ui`, runs `pnpm install` + `pnpm build`, then packages nginx like `pass-ui/Dockerfile`                                                 |




## Prerequisites

Docker Compose (Docker Desktop or recent Podman compose). The build needs network access to GitHub, Maven Central, and npm. `.env` must set `PASS_VERSION` (local image tag, e.g. `2.6.0-SNAPSHOT`), `PASS_SUPPORT_REF` / `PASS_CORE_REF` (git branch `main` for SNAPSHOT, or a release tag), and `PASS_UI_REF` (git branch, currently `uvalib`).

## Run a local stack

From `pass-docker` on `uvalib-builder`, run docker compose commands with `docker-compose-uvalib.yml` **after** `docker-compose-deposit.yml`.

Build the uvalib images:

```bash
docker compose -p pass-docker \
  -f docker-compose.yml \
  -f eclipse-pass.local.yml \
  -f docker-compose-deposit.yml \
  -f docker-compose-uvalib.yml \
  build deposit-services pass-ui
```

Start PASS core, deposit-services, and pass-ui:

```bash
docker compose -p pass-docker \
  -f docker-compose.yml \
  -f eclipse-pass.local.yml \
  -f docker-compose-deposit.yml \
  -f docker-compose-uvalib.yml \
  up -d --quiet-pull --pull missing
```

`--pull missing` keeps Compose from replacing the locally built `localhost/uvalib/deposit-services-core` and `localhost/uvalib/pass-ui` images with the GHCR ones.

`--build` is also useful in the `up` command.

PASS UI: [http://localhost:8080/app/](http://localhost:8080/app/)  
DSpace UI: [http://localhost:4000](http://localhost:4000)  

## Mapper field map

`deposit-uva/overlay/DSpaceMetadataMapper.java`  has been updated to include the following:


| PASS source              | DSpace path                                            | Notes                                               |
| ------------------------ | ------------------------------------------------------ | --------------------------------------------------- |
| manuscript title         | `/sections/publicationStep/dc.title`                   | Required                                            |
| publisher name           | `/sections/publicationStep/dc.publisher`               | Omitted if missing                                  |
| article DOI              | `/sections/publicationStep/dc.identifier.doi`          | Omitted if missing                                  |
| `"Article"`              | `/sections/publicationStep/dc.type`                    | Always set                                          |
| journal publication date | `/sections/publicationStep/dc.date.issued`             | ISO local date; now if missing                      |
| non-submitter persons    | `/sections/publicationStep/dc.contributor.author`      | Omitted if none                                     |
| manuscript abstract      | `/sections/traditionalpagetwo/dc.description.abstract` | Falls back to title, then `"No abstract provided."` |
| `"true"`                 | `/sections/license/granted`                            | Always set                                          |




## How the deposit-core image is built

[deposit-uva/Dockerfile](deposit-uva/Dockerfile)

1. Clone `pass-support` and `pass-core` at `PASS_SUPPORT_REF` / `PASS_CORE_REF` (`main` for SNAPSHOT).
2. Overlay the Java files onto `pass-deposit-services/deposit-core`.
3. `mvn -pl pass-core-test-config -am install` originally from `pass-core`, then `mvn -pl pass-deposit-services/deposit-core -am package` with `-Dtest=DSpaceMetadataMapperTest`.
4. Package with the same JRE stage as `deposit-core/Dockerfile` (`FROM eclipse-temurin:17-jre`).

The image build fails if `DSpaceMetadataMapperTest` fails.

## How the pass-ui image is built

[ui-uva/Dockerfile](ui-uva/Dockerfile)

1. Clone `uvalib/pass-ui` at `PASS_UI_REF` (branch `uvalib`).
2. `pnpm install --frozen-lockfile` and `pnpm build` with the `PASS_UI_*` values from `.env` (same host-side steps as `pass-ui/build.sh`).
3. Package with the same nginx stage as `pass-ui/Dockerfile` (`FROM nginxinc/nginx-unprivileged`). 

Rebuild after new `uvalib` commits with `--no-cache` (or set `PASS_UI_REF` to a commit SHA).