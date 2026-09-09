# Testing and local development

This page covers the test-support module and the local infrastructure packaged with this repository.

## Shared test-support module

`jeap-reaction-observer-service-test` is a reusable JAR, not just an internal test folder. Its POM publishes:

- `ReactionIdentifiedV2EventBuilder` for building `reaction-identified-event` v2 messages
- `ReactionsObservedEventBuilder` for building `reactions-observed-event` messages
- `TestReaction` and `TestObservation` model helpers
- jEAP Kafka test dependencies used by consumer-side tests

The module is consumed by the in-repo Kafka and web tests and can also be used by downstream services that test their
integration with the Reaction Observer Service contracts.

## In-repo test coverage verified

The source tree contains and the Maven reactor successfully executed tests for:

- domain graph extraction/building
- Kafka listeners for both event types
- persistence repositories and Flyway migrations
- web controllers, graph fingerprinting, and integration flows across Kafka + persistence + API

Notable verified behaviours from tests include:

- `ReactionIdentifiedEventListener` accepts full, trigger-only, and action-only payloads
- `ReactionsObservedEventListener` maps timeframe/count data into persisted `ObservedReaction` batches
- aggregated statistics can be queried after persisting identified and observed reactions
- graph endpoints return `404` for empty system/component subgraphs and variant-specific graphs for message types

## Downstream tests that replace `GraphHolder`

As of 11.0.0 the graph resources answer from `GraphHolder.getSnapshot()` - one immutable snapshot holding the
graph, the fingerprint of every subgraph and the index payloads - instead of reading `getGraph()` per request.
A downstream test that replaces the `GraphHolder` bean with a mock and stubs `getGraph()` only therefore finds
no snapshot; the resource says so rather than failing with a `NullPointerException`.

Two ways out, the second one preferable because it exercises what production does:

```java
// stub the snapshot
when(graphHolder.getSnapshot()).thenReturn(graphSnapshotFactory.of(graph));

// or use the real bean and hand it a graph
@Autowired GraphHolder graphHolder;
graphHolder.setGraph(graph);
```

## Local runtime environment

`docker/docker-compose.yml` provides:

- PostgreSQL 18.6 on port `5432`
- Kafka broker on port `9092`
- Schema Registry on port `7781`

The matching Spring profile is `application-localtest.yml` in the web module. It also configures the
authorization server the service requires as of 11.0.0 - a local jEAP OAuth mock server on port 8180. That
mock is only needed to obtain a token; the profile starts, and HTTP Basic works, without it running.

## Useful commands

```bash
./mvnw -pl jeap-reaction-observer-web -am install
./mvnw -pl jeap-reaction-observer-web test
./mvnw -pl jeap-reaction-observer-kafka test
./mvnw -pl jeap-reaction-observer-persistence test
```

A successful reactor test run used:

```bash
./mvnw -q -am -pl jeap-reaction-observer-domain,jeap-reaction-observer-kafka,jeap-reaction-observer-persistence,jeap-reaction-observer-web test
```

## Related

- [Getting started](getting-started.md)
- [Architecture](architecture.md)
- [Configuration reference](configuration.md)
- [REST API](rest-api.md)
