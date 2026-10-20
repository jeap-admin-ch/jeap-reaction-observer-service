# Getting started

This page shows how to run or package jEAP Reaction Observer Service and how it fits with the producer-side
[jEAP Reaction Observer library](https://jeap-admin-ch.github.io/docs/building-blocks/libraries/jeap-reaction-observer/getting-started).
For the service internals see [Architecture](architecture.md); for all settings see [Configuration reference](configuration.md).

## 1. Understand the split of responsibilities

Producer microservices use the sibling `jeap-reaction-observer` library to publish two Kafka event streams:

- `reaction-identified-event` v2 when a reaction pattern is first seen after startup
- `reactions-observed-event` v1 with counted observations over a timeframe

This repository is the consumer side. It ingests those events, persists them, aggregates daily observation data,
and exposes a read API for graph and statistics queries.

## 2. Choose how to consume it

Most adopters do not depend on individual modules directly. They either:

- package the reusable service via `jeap-reaction-observer-service-instance`, or
- run this repository's web module as the basis of a dedicated Reaction Observer Service deployment.

The reactor modules are described in [Architecture](architecture.md).

## 3. Provide infrastructure

The service requires:

- a PostgreSQL-compatible database in production
- Kafka plus Schema Registry through jEAP Messaging
- **an OAuth2 authorization server and a system name** - `jeap.security.oauth2.resourceserver.authorization-server.issuer`
  (with a `jwk-set-uri`) and `jeap.security.oauth2.resourceserver.system-name`. The service refuses to start
  without them and the REST API accepts bearer tokens only. See
  [Configuration](configuration.md#api-security)

For local development, `docker/docker-compose.yml` provides PostgreSQL, Kafka, and Schema Registry, and
`application-localtest.yml` contains matching sample connection properties - including an authorization server
pointing at a local [jEAP OAuth mock server](https://jeap-admin-ch.github.io/docs/building-blocks/reusable-microservices/jeap-oauth-mock-server/getting-started)
on port 8180. The mock is needed when a request carries a bearer token.

## 4. Configure the service

The Kafka topics consumed by the service are configured under `jeap.reaction.observer.service.kafka.*`:

```yaml
jeap:
  reaction:
    observer:
      service:
        kafka:
          reaction-identified-topic: applicationplatform-reaction-identified
          reactions-observed-topic: applicationplatform-reactions-observed
```

The in-repo test and local examples use simpler topic names such as `reaction-identified` and `reactions-observed`,
but the default packaged configuration points to the `applicationplatform-*` topics.

If needed, adapt the aggregation and housekeeping schedules. See [Configuration reference](configuration.md).

## 5. Start the service

The web module contains the Spring Boot application. On startup it:

- loads the latest graph into memory
- registers Kafka listeners for both event types
- schedules aggregation, housekeeping, and graph refresh jobs
- exposes the REST API under the configured servlet context path

With the default properties the servlet context path is `/jeap-reaction-observer` and the server port is `8080`.

## Related

- [Architecture](architecture.md)
- [Configuration reference](configuration.md)
- [REST API](rest-api.md)
- [Testing and local development](testing.md)
