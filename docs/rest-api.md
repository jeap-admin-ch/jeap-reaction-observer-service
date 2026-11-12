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
| `GET`  | `/api/v2/graphs`                        | read  | **API v2**: the full reaction graph with every kind of interface, plus a fingerprint                        |
| `GET`  | `/api/v2/graphs/systems`                | read  | **API v2**: the index of system graphs                                                                      |
| `GET`  | `/api/v2/graphs/components`             | read  | **API v2**: the index of component graphs                                                                   |
| `GET`  | `/api/v2/graphs/interfaces`             | read  | **API v2**: the index of interface graphs, optionally of one type (`?interfaceType=http_request`)           |
| `GET`  | `/api/v2/graphs/systems/{systemName}`   | read  | **API v2**: the subgraph for one system, or `404` if empty                                                  |
| `GET`  | `/api/v2/graphs/components/{componentName}` | read | **API v2**: the subgraph for one component, or `404` if empty                                            |
| `GET`  | `/api/v2/graphs/interfaces/{interfaceId}` | read | **API v2**: the subgraph for one interface, or `404` if the graph has no such interface                    |
| `GET`  | `/api/components/names`                 | read  | Returns all distinct non-null component names                                                              |
| `GET`  | `/api/systems/names`                    | read  | Returns all distinct non-null system names                                                                 |
| `GET`  | `/api/statistics/last-observation-date` | read  | Returns the latest aggregated observation date per component                                               |
| `GET`  | `/api/management/aggregate-data/{date}` | write | Triggers aggregation for the given `yyyy-mm-dd` date and returns a plain-text success/error message         |

The graph endpoints exist in two versions. The paths without a version (`/api/graphs/**`) are the **API v1**, which
is frozen: it keeps its format and shows messages only (see
[Messages only in the API v1](#messages-only-in-the-api-v1)). The **API v2** (`/api/v2/graphs/**`) shows every kind of
interface, see [API v2](#api-v2). The other endpoints (`/api/components/names`, `/api/systems/names`,
`/api/statistics/last-observation-date`, `/api/management/**`) have no version and are a contract as well: they
do not change with the version of the graph API.

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

The API v2 works the same way, with entity tags of its own: a fingerprint is computed per version over the
representation that version serves, so the tag of a v1 graph and the tag of the v2 graph of the same system
differ and change independently.

## Graph shape of the API v1

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

## API v2

The API v2 serves the graph as the service holds it: reactions with messages, HTTP operations and interfaces of
types the service does not know. The version of the API is not the same as the reaction model version: a new API
version is only needed for an incompatible change of this contract, not for a new reaction model version,
a new interface type or a new way to present one.

### Graph shape

```json
{
  "graph": {
    "nodes": [
      { "nodeType": "MESSAGE", "id": "I11", "interfaceType": "event", "fqn": "OrderSubmittedEvent",
        "messageType": "OrderSubmittedEvent", "variant": null },
      { "nodeType": "MESSAGE", "id": "I12", "interfaceType": "event", "fqn": "OrderCheckedEvent/eu",
        "messageType": "OrderCheckedEvent", "variant": "eu" },
      { "nodeType": "HTTP_OPERATION", "id": "I14", "interfaceType": "http_request",
        "fqn": "partner-service GET /api/partners/{}",
        "provider": "partner-service", "method": "GET", "path": "/api/partners/{}" },
      { "nodeType": "INTERFACE", "id": "I16", "interfaceType": "scheduled", "fqn": "nightly-job" },
      { "nodeType": "REACTION", "id": "R201", "component": "order-service", "system": "jme", "median": 120,
        "status": "ACTIVE", "reactionModelVersion": 1 }
    ],
    "edges": [
      { "edgeType": "TRIGGER", "sourceId": "I11", "targetId": "R201" },
      { "edgeType": "ACTION", "sourceId": "R201", "targetId": "I12" },
      { "edgeType": "ACTION", "sourceId": "R201", "targetId": "I14" },
      { "edgeType": "ACTION", "sourceId": "R201", "targetId": "I16" }
    ]
  },
  "fingerprint": "6b2f…"
}
```

**Nodes.** A node is a reaction or an interface. Every node has an `id` and a `nodeType`, and besides them the
fields of its kind only: a reaction has neither an `interfaceType` nor an `fqn`. A field of its kind that has no
value is `null` (`variant`).

| `nodeType`       | Kind of node                  | Fields                                                                  |
|------------------|-------------------------------|-------------------------------------------------------------------------|
| `REACTION`       | reaction                      | `component`, `system`, `median`, `status`, `reactionModelVersion`       |
| `INTERFACE`      | interface                     | `interfaceType` (any type the service does not present in its own way, or a known type whose FQN cannot be parsed), `fqn` |
| `MESSAGE`        | interface that is a message   | those of an interface (`interfaceType` is `event` or `command`), and `messageType`, `variant` |
| `HTTP_OPERATION` | interface that is an HTTP operation | those of an interface (`interfaceType` is `http_request`), and `provider`, `method`, `path` |

- `interfaceType` and `fqn` are the identity of an interface, as they were reported. Every interface node carries
  them, whatever its `nodeType`, so that a consumer can present a node of a kind it does not know: as an
  interface.
- `provider` is the name of the component providing the HTTP operation, as the component reported it. A service
  that does not name its operations is still shown: the caller names the operation then, with a `provider`
  starting with `//` that says where the call went, its `method` and the `path` `/{*}`. Where the call went is
  the called host, followed by the context of the called service where the caller recognised one in the path
  (e.g. `//foo.bar.admin.ch` or `//foo.bar.admin.ch/foo-service`).
- `median` is the median of the daily observation counts of the reaction within the statistics window. A
  reaction always has it, as a number from 0: it is in a graph only if it was observed within the window.
- `status` (`ACTIVE` or `SUPERSEDED`) and `reactionModelVersion` (the reaction model version of the last day the
  reaction was observed) are part of the contract. The service does not derive them yet and answers `ACTIVE` and
  `1`.

**Edges.** An edge is its `edgeType` and the ids of its two nodes. Which kind of node is at which end follows
from the type; the median is on the reaction node only. The number the v1 has on a trigger edge is the `median`
of the reaction the edge points to (`targetId`). That reaction is always part of the same response: a `TRIGGER`
or `ACTION` edge has a reaction at one of its ends, and a view selects whole reactions. A `PREDECESSOR` edge has
a reaction at both ends and is part of a response only if both reactions are. So every edge of a response has
both its nodes in it.

| `edgeType`    | `sourceId` | `targetId`                                   |
|---------------|------------|----------------------------------------------|
| `TRIGGER`     | interface  | reaction                                     |
| `ACTION`      | reaction   | interface                                    |
| `PREDECESSOR` | reaction   | a reaction that may be its predecessor; only in a response that has both reactions.Defined, the service does not answer such edges yet |

**Ids.** An id is a prefix for the kind of node and a number: `I14` is an interface, `R201` a reaction. An id is
unique within a response (in the v1 an interface and a reaction can have the same id). The prefix does not depend
on the `nodeType`, so an interface keeps its id when the service presents its type in a new way. Ids differ
between environments and may change when the database of the service is rebuilt.

### Views

A view always selects **whole** reactions: all of them, those of a system, of a component, or of an interface.
A reaction is in the view of an interface if the interface is its trigger or one of its actions, and it is
shown with its trigger and all its actions, whatever type they are. The view of an interface goes one level, as
the message graphs of the v1 do: a reaction that is only connected to one of the neighbouring interfaces is not
part of it.

There is no filter by interface type on graphs. It would either leave out reactions, or show reactions without
some of their actions that then look alike although they are counted separately. Hiding interfaces is up to the
consumer presenting the graph.

### Indexes

The indexes of systems and components have the form of the v1. The index of interfaces lists every interface of
the graph, ordered by type and FQN, and can be restricted to one type with `?interfaceType=…` (matched exactly;
a type without interfaces answers an empty index):

```json
{
  "entries": [
    {
      "id": "I14",
      "interfaceType": "http_request",
      "fqn": "partner-service GET /api/partners/{}",
      "etag": "\"sha256:6b2f…\"",
      "path": "/jeap-reaction-observer/api/v2/graphs/interfaces/I14"
    }
  ]
}
```

A consumer takes the `path` of an interface graph from the index and does not build it. The v2 has no path for
messages: a message is an interface of the type `event` or `command`, and each variant of a message type is an
interface with a graph of its own.

### Contract for consumers

The v2 is extended without a new version by adding node types, edge types, fields and values. A consumer
therefore has to:

1. Treat a node with an unknown `nodeType` and the field `interfaceType` as an interface, and present it with its
   `interfaceType` and `fqn` rather than dropping it. The same holds for an unknown `interfaceType`.
2. Resolve edges with `sourceId` and `targetId`, and ignore edges with an unknown `edgeType`.
3. Ignore unknown fields, and treat an unknown `status` as unknown.
4. Present the `status`. How is up to the consumer; the recommendation is to hide `SUPERSEDED` reactions by
   default, with a hint that reactions are hidden, and to show them on request. Only marking them is just as
   valid. `SUPERSEDED` always means presumably superseded: the v2 does not say what the supersession is based
   on. A `PREDECESSOR` edge names a possible predecessor, not a lasting reference.
5. Not store ids and paths for long, but take them from the indexes. To compare between environments or to keep
   an anchor of its own, use `interfaceType` and `fqn` for an interface, and the component and the trigger (its
   `interfaceType` and `fqn`) for a reaction group, i.e. the reactions of a component with the same trigger. A
   single reaction has no such anchor: with other actions it is another reaction.
6. Detect changes with the indexes and entity tags.
7. Decide the kind of an interface by its `interfaceType`, not by its `nodeType`, e.g. when hiding interfaces:
   the `nodeType` of an interface type changes when the service presents the type in a new way. When hiding
   interfaces, the recommendation is to keep a reaction whole - neither dropping it nor merging it with a
   reaction that then looks alike - and to mark that it has hidden interfaces.
8. Read the `provider` of an HTTP operation as the name of a component unless it starts with `//`. A `provider`
   starting with `//` says that the caller named the operation because the called service did not: the value
   then is where the call went - the called host, followed by the context of the called service where the
   caller recognised one (e.g. `//foo.bar.admin.ch` or `//foo.bar.admin.ch/foo-service`) - and the `path` is
   `/{*}`. Read the value as a whole: what follows the host is not necessarily the name of a component. Such an
   operation
   - shows a target that was called, not a provider known for sure - the host may be a gateway - and does not
     prove that the call arrived;
   - has an FQN that is specific to the environment, and is no anchor between environments (rule 5);
   - is provisional: once the called service names its operations, they take its place. The reaction observed
     until then may be shown as a predecessor of the new one, but this is not guaranteed.

## OpenAPI

`OpenApiConfig` registers two Springdoc groups and describes the API as bearer-token secured. The resources that
require a role - of the API v1, the API v2 and those without a version - name the security scheme `OIDC`, which the
jEAP swagger starter registers for the configured authorization server, so that Swagger UI can log in for them.
If Swagger UI is enabled in the surrounding deployment, it reflects these groups:

- `reaction-observer-service-api`: the API v1 and the endpoints without a version
- `reaction-observer-service-api-v2`: the API v2

Each group has the version of its API, `1.0.0` and `2.0.0`.

In the group of the v2, `nodeType`, `edgeType`, `interfaceType` and `status` are described as text with their
known values in the description, not as enumerations. The fields that are `null` when they have no value are
described with `null` as a type of theirs: `variant`, and `system` of an entry of a graph index, which is `null`
in the index of systems. The `median` of a reaction is required and a number. The kinds of node are schemas that
extend each other as in the table above (`NodeV2Dto`, extended by `ReactionNodeV2Dto` and `InterfaceNodeV2Dto`,
the latter extended by `MessageNodeV2Dto` and `HttpOperationNodeV2Dto`), and the nodes of a graph are described as
`NodeV2Dto`:

- its discriminator maps every known `nodeType` to the schema of its kind
- its extension `x-jackson-default-impl` names `InterfaceNodeV2Dto` as what a node with any other `nodeType` is
  read as

A client generated from the document therefore has a class or a type of its own for each kind of node known
today. How it reads a kind added later depends on the generator, and the contract for consumers requires an
interface. Checked with a node of an unknown `nodeType`:

- The Java client (`restclient`) of the OpenAPI generator 7.27.0 reads it as an `InterfaceNodeV2Dto`: the
  generator takes the Jackson default implementation of the generated node class from the extension.
- TypeScript generators that generate types and leave the response as it is pass it on with all its fields, to be
  read as an interface where the consumer tells the kinds apart: `@hey-api/openapi-ts` 0.99.0,
  `swagger-typescript-api` 13.14.0, `openapi-typescript` 7.13.0, and `typescript-axios` and `typescript-angular` of
  the OpenAPI generator 7.27.0. `openapi-typescript` leaves the value of `nodeType` out of the type of an HTTP
  operation.
- `typescript-fetch` and `typescript` of the OpenAPI generator 7.27.0 convert the response and keep only `id` and
  `nodeType` of it. A model template that returns the schema named by the extension for an unmapped `nodeType`
  restores the interface (checked with `typescript-fetch`).
- `orval` 8.46.0 does not generate usable types for `MessageNodeV2Dto` and `HttpOperationNodeV2Dto`, whatever the
  `nodeType`: `InterfaceNodeV2Dto` is a kind of its own and what the two extend.

None of them rejects the node. With any other generator, check that it neither rejects a node with an unknown
`nodeType` nor drops its `interfaceType` and `fqn`; otherwise give the client its fallback by other means.

A generator is not required. A client written by hand or generated by an AI from the OpenAPI document and the
contract for consumers reads the nodes just as well, and can follow the contract where a generator only knows the
document.

## Related

- [Architecture](architecture.md)
- [Configuration reference](configuration.md)
- [Testing and local development](testing.md)
