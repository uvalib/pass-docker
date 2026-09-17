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
| `[docker-compose-deposit-uva.yml](docker-compose-deposit-uva.yml)`                                       | Compose overlay: build a local UVA deposit-services image instead of pulling `ghcr.io/eclipse-pass/deposit-services-core`                                  |
| `[deposit-uva/Dockerfile](deposit-uva/Dockerfile)`                                                       | Clones `pass-support` at a git ref, overlays the UVA files, runs deposit-core's Maven package, copys the JAR into the original deposit-servcies-core image |
| `[deposit-uva/overlay/DSpaceMetadataMapper.java](deposit-uva/overlay/DSpaceMetadataMapper.java)`         | LibraOpen Publication JSON mapper                                                                                                                          |
| `[deposit-uva/overlay/DSpaceMetadataMapperTest.java](deposit-uva/overlay/DSpaceMetadataMapperTest.java)` | Unit tests run by during the image build                                                                                                                   |




## Prerequisites

Docker Compose (Docker Desktop or recent Podman compose). The build needs network access to GitHub and Maven Central (plus GHCR for the runtime base). `.env` must set `PASS_VERSION` (GHCR runtime tag), `PASS_SUPPORT_REF`, and `PASS_CORE_REF` (git tags, currently `2.5.1`).

## Run a local stac

From `pass-docker`, run docker compose commands with `docker-compose-deposit-uva.yml` **after** `docker-compose-deposit.yml`

Build the mapper image (also runs `DSpaceMetadataMapperTest`):

```bash
docker compose -p pass-docker \
  -f docker-compose.yml \
  -f eclipse-pass.local.yml \
  -f docker-compose-deposit.yml \
  -f docker-compose-deposit-uva.yml \
  build deposit-services
```

Start PASS, and deposit-services:

```bash
docker compose -p pass-docker \
  -f docker-compose.yml \
  -f eclipse-pass.local.yml \
  -f docker-compose-deposit.yml \
  -f docker-compose-deposit-uva.yml \
  up -d --quiet-pull --pull missing
```

`--pull missing` keeps Compose from replacing the locally built `localhost/uvalib/deposit-services-core` image with the GHCR one.

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

1. Clone `pass-support` and `pass-core` at the required git tags `PASS_SUPPORT_REF` / `PASS_CORE_REF` (e.g. `2.5.1`).
2. Overlay the two UVA Java files onto `pass-deposit-services/deposit-core`.
3. `mvn -pl pass-core-test-config -am install` from `pass-core`, then `mvn -pl pass-deposit-services/deposit-core -am package` with `-Dtest=DSpaceMetadataMapperTest` (reused from upstream deposit-core ).
4. Copy `deposit-core-*-exec.jar` into `FROM ghcr.io/eclipse-pass/deposit-services-core:${PASS_VERSION}` (keeps upstream entrypoint, user, and JRE).

The image build fails if `DSpaceMetadataMapperTest` fails.