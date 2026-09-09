# REST API

This page summarises the API surface verified in `jeap-reaction-observer-web`. All endpoints live under the servlet
context path, which defaults to `/jeap-reaction-observer`.

## Authentication and authorization

The API accepts **two ways of authenticating**, on the same paths:

| Mechanism        | Credentials                                                                                     | Available                                             |
|------------------|-------------------------------------------------------------------------------------------------|-------------------------------------------------------|
| HTTP Basic       | the two configured users, with the roles `reaction-observer-read` and `reaction-observer-write`  | always                                                |
| OAuth2 (bearer)  | a token carrying the semantic role `<system-name>_@reactions_#read` or `..._#write`             | when the instance is configured as a resource server  |

The semantic role has **no tenant part**: a tenant says which mandant may exercise a role, and there is no such
division here - a system's subgraph carries the messages of other systems by construction, and a consumer that
documents a landscape reads every system of it.

A bearer token is accepted only when the instance configures an issuer
(`jeap.security.oauth2.resourceserver.authorization-server.issuer`), because without one there is nothing to
validate a token with; and the semantic role is only evaluated when a system name
(`jeap.security.oauth2.resourceserver.system-name`) is configured, which is what activates semantic
authorization in the jEAP security starter. An instance that configures neither behaves exactly as it did
before: HTTP Basic, and a bearer token is refused.

A token may also carry the *simple* role `reaction-observer-read` or `reaction-observer-write` instead, which
is what lets an authorization server grant either spelling while its consumers move.

`WebSecurityConfig` permits HTTP `GET` requests to `/api/**` at the filter-chain level and requires anything
else to be authenticated; every controller method is authorized with
`@PreAuthorize("@reactionsApiAuthorization.canRead()")` or `...canWrite()`, which is where the two mechanisms
meet. `ReactionsApiRoleCoverageTest` fails the build if a handler appears without it.

**No CSRF token is needed.** The API is stateless and authenticated per request, so its own filter chain
disables CSRF protection - for both mechanisms. That is also why bearer tokens are handled by this chain
rather than left to the starter's, which enables CSRF with a cookie repository.

## Endpoints

| Method | Path                                    | Role  | Behaviour                                                                                                  |
|--------|-----------------------------------------|-------|------------------------------------------------------------------------------------------------------------|
| `GET`  | `/api/graphs`                           | read  | Returns the full in-memory reaction graph plus a fingerprint                                               |
| `GET`  | `/api/graphs/systems`                   | read  | **The index of system graphs**: every system that has a reaction, with the entity tag of its graph          |
| `GET`  | `/api/graphs/components`                | read  | **The index of component graphs**, each entry also naming the system the component's reactions came from    |
| `GET`  | `/api/graphs/messages`                  | read  | **The index of message-type graphs**, each entry listing the variants it answers with                       |
| `GET`  | `/api/graphs/systems/{systemName}`      | read  | Returns the subgraph for one producer system, or `404` if empty                                             |
| `GET`  | `/api/graphs/components/{componentName}`| read  | Returns the subgraph for one component, or `404` if empty                                                  |
| `GET`  | `/api/graphs/messages/{messageType}`    | read  | Returns one graph per variant of the given message type; the key is `messageType` or `messageType/variant`  |
| `GET`  | `/api/components/names`                 | read  | Returns all distinct non-null component names                                                              |
| `GET`  | `/api/systems/names`                    | read  | Returns all distinct non-null system names                                                                 |
| `GET`  | `/api/statistics/last-observation-date` | read  | Returns the latest aggregated observation date per component                                               |
| `GET`  | `/api/management/aggregate-data/{date}` | write | Triggers aggregation for the given `yyyy-mm-dd` date and returns a plain-text success/error message         |

## The indexes, and conditional requests

Every graph resource and every index carries an `ETag` and honours `If-None-Match` with a `304 Not Modified`.

**What an index is for.** Finding out whether anything changed used to cost one request per system, per
component and per message type - and the fingerprint that answers the question sits *inside* the payload. An
index answers it in one call: it lists what is **available** (rather than what changed, which would make
"unchanged" and "gone" indistinguishable) with the entity tag of each graph, so a consumer knows what to fetch
before it asks.

The entity tag of an index entry is **the same string** the graph resource answers with, so the comparison
needs no request:

```json
{
  "entries": [
    {
      "name": "orders-intake",
      "system": "orders",
      "etag": "\"sha256:6b2f…\"",
      "path": "/api/graphs/components/orders-intake"
    }
  ]
}
```

Only names whose graph is **not empty** are listed, so an entry never points at a `404`. Note that
`/api/systems/names` and `/api/components/names` are different: they answer every name that ever had a
reaction identified, including those whose reactions have not been observed within the statistics period.

A tag is `"sha256:<hex>"`. For a graph it is the fingerprint the body also carries - computed over the
canonicalized graph, so it names the graph rather than the exact bytes; for an index it is the hash of the
bytes written. The tags of all variants of a message type are combined into one, because that resource
answers all of them at once: a variant appearing or disappearing moves it.

Both the tags and the indexes come from a snapshot built when the graph is refreshed, so neither an index nor
a `304` extracts or serializes a graph.

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
