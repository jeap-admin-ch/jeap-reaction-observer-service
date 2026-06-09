# AGENTS.md

This file provides guidance to coding agents when working with code in this repository.

## What this is

`jeap-reaction-observer-service` is a jEAP library serving as the basis for a re-usable microservice (not a standalone
deployable). It records and aggregates *triggers*, *actions*, and *reactions* derived from Kafka events and exposes the
resulting reaction graph and statistics via a REST API. Concrete services depend on this library and provide their own
`*-service-instance` deployment; the `jeap-reaction-observer-service-instance` module here is the in-repo packaging used
for building/testing the full application.

Published to Maven Central. Versioned with Semantic Versioning; every change must be recorded in `CHANGELOG.md` (Keep a
Changelog format). Spring Boot 4 lives on `master`.

## Build & test

```bash
./mvnw clean install                 # full build + tests
./mvnw test                          # all tests
./mvnw -pl jeap-reaction-observer-web test          # one module
./mvnw -pl jeap-reaction-observer-web test -Dtest=GraphControllerTest         # one test class
./mvnw -pl jeap-reaction-observer-web test -Dtest=GraphControllerTest#methodName   # one test method
```

Integration tests in `jeap-reaction-observer-web` use Testcontainers (PostgreSQL) and an embedded Kafka, so a running
Docker daemon is required. Repository/persistence tests run against H2.

`docker/docker-compose.yml` provides Kafka + Postgres for running the app locally (profile `localtest`, see
`application-localtest.yml`).

## Module architecture

The build is a Maven reactor. Dependencies flow **domain ← {kafka, persistence, web}**, with `web` wiring everything
together:

- **`jeap-reaction-observer-domain`** — Pure domain: models, repository *interfaces* (`ReactionGraphRepository`,
  `ObservedReactionRepository`, `ObservedReactionsAggregatedRepository`, etc.), and domain services (
  `ReactionGraphBuilderService`, `GraphExtractor`, `aggregation/AggregationService`). Note two distinct model packages:
  `models` (persistence-facing: `Reaction`, `Observation`, `ObservedReaction`) and `models/graph` (the graph
  representation: `Node`/`Message`/`Reaction`, `Edge`/`Trigger`/`Action`, `Graph`).
- **`jeap-reaction-observer-kafka`** — Event listeners (`ReactionIdentifiedEventListener`,
  `ReactionsObservedEventListener`) that ingest the jEAP message types `reaction-identified-event` (v1 + v2, the v2
  added as a classified artifact) and `reactions-observed-event`. This is the write path that records observations.
- **`jeap-reaction-observer-persistence`** — JPA entities + repository *implementations* of the domain interfaces,
  Flyway migrations under `src/main/resources/db/migration/common`, and ShedLock JDBC table. PostgreSQL in prod, H2 in
  tests.
- **`jeap-reaction-observer-web`** — Spring Boot application (`ReactionObserverApplication`), REST controllers under
  `api/`, DTOs under `models/graph`, security config, and `ScheduledTasksService`. Holds the assembled graph in memory
  via `GraphHolder`.
- **`jeap-reaction-observer-service-test`** — Shared test fixtures/builders (Avro message helpers) reused across
  modules' tests.
- **`jeap-reaction-observer-service-instance`** — `pom` packaging that bundles `web` into the runnable instance;
  disables the license plugins inherited from the parent.

Each library module ships a Spring Boot `AutoConfiguration` (registered in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`), so depending on a module is enough
to activate it.

## Key runtime flows

- **Write path:** Kafka listeners persist observed reactions as they arrive.
- **Aggregation & housekeeping:** `ScheduledTasksService` (cron-driven, coordinated across instances by ShedLock)
  aggregates per-day reaction statistics, refreshes the in-memory graph, and runs housekeeping that deletes old
  `ObservedReaction` rows and aggregated data older than `statisticsPeriodInDays`.
- **Graph build:** `ReactionGraphBuilderService.buildGraph(fromDate)` builds the full graph from the repository and
  enriches `Trigger` edges with median trigger→reaction durations computed over the statistics window. The result is
  cached in `GraphHolder` and served by `GraphController`. `GraphExtractor` derives filtered subgraphs (by system,
  component, or message type/variant) on read.
- **Read path:** `GraphController` serves the full graph and subgraphs, each wrapped with a content fingerprint (
  `GraphFingerprintCalculator`, canonical-JSON based) so clients (e.g. ArchRepo) can detect changes. Endpoints are
  secured with `@PreAuthorize("hasAnyRole('reaction-observer-read')")`.

## Conventions

- Lombok is used throughout (`@AllArgsConstructor`, `@Slf4j`, builders); `target/delombok` output is generated, not
  source.
- Configurable behavior is exposed via `ReactionObserverProperties` / `ReactionObserverKafkaProperties` with defaults in
  `reactionObserverDefaultProperties.properties`. Cron expressions and `statisticsPeriodInDays` are the main tuning
  knobs.
- Database schema changes are Flyway migrations only — add a new `V*__*.sql` under `db/migration/common`, never edit an
  applied one.

## Versioning

- Semantic Versioning; all changes documented in [CHANGELOG.md](./CHANGELOG.md) (Keep a Changelog format).
- `setPomVersions.sh` updates the version across all module POMs.
- When working on a feature branch, increase the version to `x.y.z-SNAPSHOT` in the POMs.
- When bumping the version, also  update the changelog, and updates version/date in `publiccode.yml`.
- When the version on a feature branch has not yet been bumped compared to master, ask the user if a major, minor or patch version bump should be performed, and update the version accordingly.
