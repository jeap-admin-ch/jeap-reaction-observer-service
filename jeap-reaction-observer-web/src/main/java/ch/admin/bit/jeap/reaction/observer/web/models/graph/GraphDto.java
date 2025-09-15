package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import java.util.List;

/**
 * Data structure representing a directed graph of nodes and edges.
 * Nodes can be messages or reactions, and edges define their relationships.
 * Used for modeling event-driven flows and process interactions.
 */
public record GraphDto(
        List<NodeDto> nodes,
        List<EdgeDto> edges
) {
}
