# REST API

This page summarises the API surface verified in `jeap-reaction-observer-web`. All endpoints live under the servlet
context path, which defaults to `/jeap-reaction-observer`.

## Authentication and authorization

The API uses HTTP Basic authentication with two configured users:

- read user with role `reaction-observer-read`
- write user with role `reaction-observer-write`

`WebSecurityConfig` permits HTTP `GET` requests to `/api/**` at the filter-chain level, but every controller method is
also protected with `@PreAuthorize`. In practice, the documented endpoints require the matching role.

## Endpoints

| Method | Path | Role | Behaviour |
|---|---|---|---|
| `GET` | `/api/graphs` | read | Returns the full in-memory reaction graph plus a fingerprint |
| `GET` | `/api/graphs/systems/{systemName}` | read | Returns the subgraph for one producer system, or `404` if empty |
| `GET` | `/api/graphs/components/{componentName}` | read | Returns the subgraph for one component, or `404` if empty |
| `GET` | `/api/graphs/messages/{messageType}` | read | Returns one graph per variant of the given message type; the key is `messageType` or `messageType/variant` |
| `GET` | `/api/components/names` | read | Returns all distinct non-null component names |
| `GET` | `/api/systems/names` | read | Returns all distinct non-null system names |
| `GET` | `/api/statistics/last-observation-date` | read | Returns the latest aggregated observation date per component |
| `GET` | `/api/management/aggregate-data/{date}` | write | Triggers aggregation for the given `yyyy-mm-dd` date and returns a plain-text success/error message |

## Graph shape

`GraphController` returns `GraphWithFingerprintDto`, which wraps:

- `graph.nodes`: message nodes and reaction nodes
- `graph.edges`: trigger and action edges
- `fingerprint`: a canonical-JSON-based hash for change detection

The graph DTO distinguishes:

- message nodes by message type and optional variant
- reaction nodes by id and component name
- trigger edges with an optional `median`
- action edges without the median field

## OpenAPI

`OpenApiConfig` registers a Springdoc group named `reaction-observer-service-api` for `/api/**` and describes the API as
HTTP Basic secured. If Swagger UI is enabled in the surrounding deployment, it reflects this group.

## Related

- [Architecture](architecture.md)
- [Configuration reference](configuration.md)
- [Testing and local development](testing.md)
