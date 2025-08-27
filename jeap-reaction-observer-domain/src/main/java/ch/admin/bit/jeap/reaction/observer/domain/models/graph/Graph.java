package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

import java.util.List;

/**
 * Data structure representing a directed graph of nodes and edges.
 * Nodes reference edges only if bidirectional traversal (node->edge and edge->node) is needed.
 * Edges connect nodes and are stored centrally for efficient access.
 */
public record Graph(
        List<Node> nodes,
        List<Edge> edges
) {}
