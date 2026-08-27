# AGENTS.md

Guidance for AI coding agents working **in this repository**. For how to *use* the service, read [README.md](README.md)
and the [docs/](docs/) folder instead.

## Project

jEAP Reaction Observer Service is a multi-module Maven library for building a reusable microservice that consumes
reaction events from services using the jEAP Reaction Observer client library, persists identified reactions and
observed counts, aggregates recent observations into a graph-oriented read model, and exposes that model via a small
REST API. Downstream projects can depend on `jeap-reaction-observer-service-instance` to package the service, while
this repository contains the shared domain, Kafka ingestion, persistence, web/API, and test-support modules.

## Repository layout

```text
pom.xml                                                  # Parent POM (packaging=pom); declares the modules below
jeap-reaction-observer-domain/                           # Domain services and graph model
  src/main/java/ch/admin/bit/jeap/reaction/observer/domain/
    aggregation/                                         # Daily aggregation and retention services
    models/                                              # Identified/observed reaction model
    models/graph/                                        # Graph nodes/edges served by the API
    *.java                                               # Repository interfaces, GraphExtractor, graph builder
jeap-reaction-observer-kafka/                            # Kafka listeners for identified/observed events
  src/main/java/ch/admin/bit/jeap/reaction/observer/kafka/
    ReactionIdentifiedEventListener.java                 # Persists reaction definitions from reaction-identified events
    ReactionsObservedEventListener.java                  # Persists observed counts from reactions-observed events
    ReactionObserverKafkaProperties.java                 # Topic configuration binding
jeap-reaction-observer-persistence/                      # JPA entities, JDBC/JPA repository implementations, Flyway schema
  src/main/resources/db/migration/common/                # Schema + evolution migrations, incl. ShedLock table
jeap-reaction-observer-web/                              # Spring Boot application, REST API, scheduling, security
  src/main/java/ch/admin/bit/jeap/reaction/observer/web/api/
    GraphController.java                                 # Full graph and filtered subgraph endpoints
    ComponentController.java                             # Known component names
    SystemController.java                                # Known system names
    StatisticsController.java                            # Last observation date per component
    ManagementController.java                            # Manual aggregation trigger
  src/main/resources/
    reactionObserverDefaultProperties.properties         # Default service properties
    application-localtest.yml                            # Local profile using Postgres + Kafka from docker-compose
jeap-reaction-observer-service-test/                     # Shared Avro event builders and test models for consumers/tests
jeap-reaction-observer-service-instance/                 # POM-only parent packaging the runnable service instance
Docker/ and root files: docker/docker-compose.yml, Jenkinsfile, CHANGELOG.md, publiccode.yml, LICENSE
```

## Build & test

```bash
./mvnw -pl jeap-reaction-observer-web -am install    # build the runnable service module and dependencies
./mvnw verify                                        # full build incl. tests
./mvnw -pl jeap-reaction-observer-web test           # web/API module tests
```

- Parent: `ch.admin.bit.jeap:jeap-spring-boot-parent`.
- Kafka listener tests use the jEAP Kafka integration test support with an embedded broker.
- Persistence tests run Flyway migrations against H2.
- Integration tests in `jeap-reaction-observer-web` use Testcontainers/PostgreSQL plus Kafka, so a running Docker daemon is required.

## jEAP conventions

- Java packages live under `ch.admin.bit.jeap.reaction.observer...`.
- The producer side is documented in the sibling `jeap-reaction-observer` library; this repository documents the consumer/aggregator side only.
- `ReactionIdentifiedEventListener` stores reaction definitions, while `ReactionsObservedEventListener` stores counted observations keyed by the event idempotence id.
- The graph served by the API is rebuilt from persisted reactions and filtered to reactions observed within the configured statistics window; trigger edges are enriched with median values computed from aggregated daily counts.
- Security is HTTP Basic with in-memory read/write users from configuration; read endpoints require role `reaction-observer-read`, manual aggregation requires `reaction-observer-write`.
- Database changes are Flyway migrations only — add new `V*__*.sql` files under `db/migration/common`, never edit applied migrations.

## Docs

When changing public behaviour, update the matching focused file under [docs/](docs/) (one topic per file) and
the documentation index in the README.

- Pages must be valid MDX (Docusaurus renders every `.md` as MDX) and any Mermaid diagrams must use correct Mermaid syntax — see the [writing principles](https://github.com/jeap-admin-ch/jeap/blob/master/docs/documenting-jeap.md#writing-principles). There is no standalone linter for this; validate by actually building the docs site locally against this checkout, using the [site repository](https://github.com/jeap-admin-ch/jeap-admin-ch.github.io)'s `preview.sh --local <path-to-this-repo> --no-autodiscover` (production build, catches MDX/Mermaid syntax errors and broken links) or `dev.sh` for a faster hot-reload check.

## Versioning

- Semantic Versioning; all changes documented in [CHANGELOG.md](./CHANGELOG.md) (Keep a Changelog format).
- `setPomVersions.sh` updates the version across all module POMs.
- When working on a feature branch, increase the version to `x.y.z-SNAPSHOT` in the POMs.
- Always keep the -SNAPSHOT postfix in the POMs, CI will remove it when releasing a version. Do not use the SNAPSHOT postfix in other places (CHANGELOG, publiccode.yml etc.)
- Keep changelog entries concise and to the point, follow existing patterns.
- Keep commit messages short, use the JIRA ID from the branch name as a prefix, do not use conventional commits (for example: "JEAP-1234 Added feature X").
- When bumping the version, also update the changelog, and update version/date in `publiccode.yml`.
- When the version on a feature branch has not yet been bumped compared to master, ask the user if a major, minor or patch version bump should be performed, and update the version accordingly.
