# Configuration reference

This page lists the properties verified in source code and default resources of jEAP Reaction Observer Service.
For local infrastructure examples see [Testing and local development](testing.md).

## Service properties

| Property | Required | Default / example | Verified from |
|---|---|---|---|
| `jeap.reaction.observer.service.kafka.reaction-identified-topic` | yes | `applicationplatform-reaction-identified` in packaged defaults; `reaction-identified` in tests/local example | `ReactionObserverKafkaProperties`, `reactionObserverDefaultProperties.properties`, `application-localtest.yml` |
| `jeap.reaction.observer.service.kafka.reactions-observed-topic` | yes | `applicationplatform-reactions-observed` in packaged defaults; `reactions-observed` in tests/local example | same as above |
| `jeap.reaction.observer.service.statistics-period-in-days` | yes | `30` | `ReactionObserverProperties`, default properties |
| `jeap.reaction.observer.service.data-aggregation-cron-expression` | yes | `0 0 1 * * *` | `ReactionObserverProperties`, default properties |
| `jeap.reaction.observer.service.housekeeping-observed-reactions-cron-expression` | yes | `0 0 3 * * *` | `ReactionObserverProperties`, default properties |
| `jeap.reaction.observer.service.housekeeping-aggregated-data-cron-expression` | yes | `0 0 5 * * *` | `ReactionObserverProperties`, default properties |
| `jeap.reaction.observer.service.graph-refresh-cron-expression` | yes | `0 0 6 * * *` | `ScheduledTasksService`, default properties |

## API security users

| Property | Required | Default / example | Purpose |
|---|---|---|---|
| `jeap.reaction.observer.read-user.username` | yes | `read` | HTTP Basic user for read endpoints |
| `jeap.reaction.observer.read-user.password` | yes | no safe default documented; tests use `{noop}secret` | Password for read user |
| `jeap.reaction.observer.write-user.username` | yes | `write` | HTTP Basic user for management endpoint |
| `jeap.reaction.observer.write-user.password` | yes | no safe default documented | Password for write user |

`WebSecurityConfig` creates in-memory users with roles `reaction-observer-read` and `reaction-observer-write`.
Method security then restricts the endpoints documented in [REST API](rest-api.md).

## Spring Boot defaults shipped by the web module

The web module also ships these operational defaults in `reactionObserverDefaultProperties.properties`:

| Property | Default |
|---|---|
| `spring.application.name` | `jeap-reaction-observer` |
| `server.servlet.context-path` | `/${spring.application.name}` |
| `server.port` | `8080` |
| `spring.jpa.open-in-view` | `false` |
| `spring.jpa.hibernate.ddl-auto` | `validate` |
| `spring.datasource.type` | `com.zaxxer.hikari.HikariDataSource` |
| `spring.datasource.hikari.maximum-pool-size` | `10` |
| `spring.flyway.enabled` | `true` |
| `spring.flyway.locations` | `classpath:db/migration/common` |
| `jeap.swagger.status` | `OPEN` |

## Local profile example

`application-localtest.yml` shows one supported local setup:

- PostgreSQL on `jdbc:postgresql://localhost:5432/sample-db`
- Kafka bootstrap server `http://localhost:9092`
- Schema Registry `http://localhost:7781`
- topic names `reaction-identified` and `reactions-observed`

It also configures `jeap.messaging.kafka.system-name`, `monitor.prometheus.password`, and PostgreSQL driver details.
Those settings come from the surrounding jEAP platform libraries rather than from this repository's own configuration classes.

## Scheduling semantics

The schedules are implemented in `ScheduledTasksService` as follows:

- aggregation job: aggregates yesterday's raw observations
- observed-reaction housekeeping: deletes raw observations before today
- aggregated-data housekeeping: deletes daily aggregates older than `statistics-period-in-days`
- graph refresh: rebuilds the in-memory graph from reactions observed within the same statistics window

All scheduled jobs use ShedLock, so only one service instance executes a given job at a time.

## Related

- [Getting started](getting-started.md)
- [Architecture](architecture.md)
- [REST API](rest-api.md)
- [Testing and local development](testing.md)
