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
| `jeap.reaction.observer.service.housekeeping-observed-reactions-batch-size` | yes | `1000` | `ReactionObserverProperties`, default properties |
| `jeap.reaction.observer.service.housekeeping-observed-reactions-max-deletions-per-run` | yes | `500000` | `ReactionObserverProperties`, default properties |
| `jeap.reaction.observer.service.housekeeping-aggregated-data-cron-expression` | yes | `0 0 5 * * *` | `ReactionObserverProperties`, default properties |
| `jeap.reaction.observer.service.graph-refresh-cron-expression` | yes | `0 0 6 * * *` | `ScheduledTasksService`, default properties |

## API security

The API accepts **OAuth2 bearer tokens only**. Both properties below are required, and the service refuses
to start without them. They belong to the jEAP security starter:

| Property                                                            | Required | Default / example                      | Purpose                                                                                                   |
|---------------------------------------------------------------------|----------|----------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `jeap.security.oauth2.resourceserver.authorization-server.issuer`   | **yes**  | `https://keycloak.example.ch/realms/x` | The token issuer - what makes the service a resource server. Give it a `jwk-set-uri` as well, or an issuer the starter can discover one from |
| `jeap.security.oauth2.resourceserver.system-name`                   | **yes**  | `myplatform`                           | Activates semantic authorization, and is the first part of the role name: `<system-name>_@reactions_#read` |

Both properties are checked at startup: the issuer lets the service validate tokens, and the system name
activates semantic-role authorization. The API grants read access to
`<system-name>_@reactions_#read` and aggregation access to `<system-name>_@reactions_#write`. No HTTP Basic
users or password properties are required or supported.

The role carries **no tenant part**; see [REST API](rest-api.md) for why, and for the CSRF behaviour of the
API.

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
- observed-reaction housekeeping: deletes raw observations with an observation date before today, in batches of
  `housekeeping-observed-reactions-batch-size` rows that each commit on their own, so a run that is interrupted
  keeps the progress it made. A run stops after `housekeeping-observed-reactions-max-deletions-per-run` rows and
  leaves the rest to the next runs, so working off a large backlog does not keep the job running for hours. Reaching
  that limit is logged as a warning. The limit is also the most the table can shrink per run, so keep it above the
  number of observed reactions ingested per day, or the backlog never shrinks. Both values must be at least 1,
  otherwise the service does not start
- aggregated-data housekeeping: deletes daily aggregates older than `statistics-period-in-days`
- graph refresh: rebuilds the in-memory graph from reactions observed within the same statistics window

The three jobs that write hold a ShedLock, so exactly one instance performs each of them. The graph
refresh does **not**, and must not: the graph is held per JVM and rebuilding it writes nothing, so a
lock would leave every instance but the one that won it serving the graph it built while starting - see
[Architecture](architecture.md).

## Related

- [Getting started](getting-started.md)
- [Architecture](architecture.md)
- [REST API](rest-api.md)
- [Testing and local development](testing.md)
