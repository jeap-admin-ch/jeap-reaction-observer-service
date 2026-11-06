# Metrics

Besides the standard metrics of a jEAP service, the Reaction Observer Service exports the following. The names are
as Prometheus scrapes them.

## What the service accepts without being able to use it

| Metric | Type | Meaning |
|---|---|---|
| `reaction_observer_service_unknown_reaction_counts_total` | counter | Counts of a `reactions-observed-event` that were skipped because their reaction id has not been identified. The other counts of the event are saved. |
| `reaction_observer_service_unparsable_interfaces_total{type}` | counter | Interfaces of a known observation type (`event`, `command`, `http_request`) whose FQN could not be parsed. They are shown as generic interfaces in the internal graph and are not in the API v1. Counted once per interface and instance, not on every graph refresh. |

A rising `reaction_observer_service_unknown_reaction_counts_total` means counts arrive before the identification
of their reaction and are lost - see [Architecture](architecture.md#observed-counts). It is expected to rise
briefly while a backlog is consumed after a deployment; a lasting rise points to reactions whose identification
does not reach the service.

The warnings logged with these metrics name the component, the reaction ids, or the interface concerned.

## Timings

Each timer is exposed by Prometheus with the suffixes `_count`, `_sum` and `_max` appended to the name below.

| Metric | Meaning |
|---|---|
| `reaction_observer_service_reaction_graph_refresh_seconds` | Duration of the scheduled graph refresh |
| `reaction_observer_service_graph_snapshot_seconds` | Duration of building the snapshot (subgraphs, fingerprints and indexes) of a refreshed graph |
| `reaction_observer_service_aggregate_data_seconds` | Duration of the daily aggregation |

## Related

- [Architecture](architecture.md)
- [Configuration reference](configuration.md)
