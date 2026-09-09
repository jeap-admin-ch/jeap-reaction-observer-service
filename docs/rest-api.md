# REST API

This page summarises the API surface verified in `jeap-reaction-observer-web`. All endpoints live under the servlet
context path, which defaults to `/jeap-reaction-observer`.

## Authentication and authorization

The API accepts **two ways of authenticating**, on the same paths:

| Mechanism        | Credentials                                                                                     | Available |
|------------------|-------------------------------------------------------------------------------------------------|-----------|
| HTTP Basic       | the two configured users, with the roles `reaction-observer-read` and `reaction-observer-write`  | always    |
| OAuth2 (bearer)  | a token carrying the semantic role `<system-name>_@reactions_#read` or `..._#write`             | always    |

**Both are required to be configured.** An instance that configures no authorization server, or one without a
system name, **does not start** - see [Configuration](configuration.md). There is no fallback to HTTP Basic
alone: an instance serving only passwords would look healthy while being unusable to a consumer that
authenticates with a token.

**One mechanism, one rule.** A password is authorized by the in-memory user's role, a token by its semantic
role - and by nothing else. A token carrying the simple role `reaction-observer-read` is refused, so that what
a grant means does not depend on how the caller connected.

The semantic role has **no tenant part**: a tenant says which mandant may exercise a role, and there is no such
division here - a system's subgraph carries the messages of other systems by construction, and a consumer that
documents a landscape reads every system of it.

Both properties are checked while the service starts, and either missing one stops it:
`...authorization-server.issuer`, because without it there is nothing to validate a token with; and
`...system-name`, because that is what makes the jEAP security starter evaluate semantic roles - without it
the API would accept tokens and authorize none of them.

`WebSecurityConfig` permits HTTP `GET` requests to `/api/**` at the filter-chain level and requires anything
else to be authenticated; every controller method is authorized with
`@PreAuthorize("@reactionsApiAuthorization.canRead()")` or `...canWrite()`, which is where the two mechanisms
meet - one bean with one branch per mechanism, because the starter's two-argument `hasRole(resource,
operation)` only exists on the expression root it installs for a token and would not resolve at all on a
basic-auth request. `ReactionsApiRoleCoverageTest` fails the build if a handler appears without it.

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
needs no request. The `path` carries the servlet context path, so it can be fetched as it stands:

```json
{
  "entries": [
    {
      "name": "orders-intake",
      "system": "orders",
      "etag": "\"sha256:6b2f…\"",
      "path": "/jeap-reaction-observer/api/graphs/components/orders-intake"
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

Both the tags and the indexes come from a snapshot built when the graph is refreshed: the index payloads are
serialized and tagged there, once, so serving an index writes bytes that already exist and answering `304` -
to an index or to a graph - writes none and extracts nothing. A graph resource that does answer with a body
extracts its subgraph, but takes the fingerprint from the snapshot rather than computing it again.

Every handler reads that snapshot **once** per request, so the tag and the body always describe the same
graph even when a refresh lands in between.

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
