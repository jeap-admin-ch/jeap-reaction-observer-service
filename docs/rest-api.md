# REST API

This page summarises the API surface verified in `jeap-reaction-observer-web`. All endpoints live under the servlet
context path, which defaults to `/jeap-reaction-observer`.

## Authentication and authorization

The API accepts **OAuth2 bearer tokens only**. Each token must carry the semantic role
`<system-name>_@reactions_#read` to access read endpoints or `<system-name>_@reactions_#write` to trigger
aggregation. HTTP Basic credentials are not supported.

The authorization server issuer and system name are required; the service refuses to start without either.
See [Configuration](configuration.md#api-security).

The semantic role has **no tenant part**: a tenant says which mandant may exercise a role, and there is no such
division here - a system's subgraph carries the messages of other systems by construction, and a consumer that
documents a landscape reads every system of it.

The controller methods enforce those roles with jEAP semantic checks, for example
`@PreAuthorize("hasRole('reactions', 'read')")`. `ReactionsApiRoleCoverageTest` fails the build if an API
handler lacks one of the read/write checks.

**No CSRF token is needed.** The API is stateless and authenticated per request, so its filter chain disables
CSRF protection. It handles bearer tokens directly rather than leaving them to the starter's default chain,
which enables CSRF with a cookie repository.

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

- `graph.nodes`: message nodes and reaction nodes (see [Messages only in the API v1](#messages-only-in-the-api-v1))
- `graph.edges`: trigger and action edges
- `fingerprint`: a canonical-JSON-based hash for change detection

The graph DTO distinguishes:

- message nodes by message type and optional variant
- reaction nodes by id, component name and an optional `median`
- trigger edges with an optional `median`
- action edges without the median field

The `median` is counted per reaction - the median of its daily observation counts within the statistics
window - so a reaction node and every trigger edge into it carry the same number. It is on the node as well
because a reaction that no message triggered has no trigger edge to carry it, and such a reaction is still
observed and counted.

## Messages only in the API v1

The API v1 shows what model version 1 of the reaction observer library observes: messages. Libraries can report
other interfaces as well (HTTP requests, and types this service does not know yet), and the v1 hides them from its
consumers with the same format as before:

- interfaces that are not messages (any type other than `event` and `command`) are removed, with their edges
- reactions left without any message are removed, e.g. a reaction triggered by an HTTP request with no message
  action
- reactions triggered by something other than a message are shown without a trigger, with their message actions

The indexes, entity tags and fingerprints are all computed over this view, and every edge of the v1 still points
to a `MESSAGE` node. A component or system whose reactions have no message part has no v1 graph (`404`) and is not
in the indexes.

This applies to the graph endpoints only. `/api/components/names`, `/api/systems/names` and
`/api/statistics/last-observation-date` are not filtered: a component with only HTTP reactions is listed there and
shown as recently observed, although its graph answers `404`.

These consequences are accepted:

1. Where model version 1 reported one action-only reaction per message (`→ A`, `→ B`), the v1 shows one reaction
   without a trigger and with several actions (`→ {A, B}`) after the component upgrades the library.
2. Actions of failed HTTP requests that model version 1 counted although they were rolled back (phantom actions)
   are no longer shown.
3. For an upgraded component, its old and new reactions are shown side by side until the old ones fall out of the
   statistics window, as with any change of reaction ids.
4. The median of a reaction is computed over its days of observation within the window only. After an upgrade on
   day 10, the old reaction shows the median of days 1 to 10 and the new one the median of days 10 to 30, both at
   the full daily value although the daily total was never doubled; only on the day of the upgrade are the counts
   split between the two.

## OpenAPI

`OpenApiConfig` registers a Springdoc group named `reaction-observer-service-api` for `/api/**` and describes the API
as bearer-token secured. If Swagger UI is enabled in the surrounding deployment, it reflects this group.

## Related

- [Architecture](architecture.md)
- [Configuration reference](configuration.md)
- [Testing and local development](testing.md)
