package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Node;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;

public enum NodeDtoType {
    MESSAGE,
    REACTION;

    /**
     * The type of a node in the API v1, which knows messages and reactions only. The type comes from the node an edge
     * points to, not from the edge.
     *
     * @throws IllegalArgumentException for any other node, which the API v1 does not serve - its graph is the view
     *                                  of {@code MessageGraphFilter}
     */
    public static NodeDtoType of(Node node) {
        if (node instanceof Message) {
            return MESSAGE;
        }
        if (node instanceof Reaction) {
            return REACTION;
        }
        throw new IllegalArgumentException("The API v1 only has message and reaction nodes, not " +
                                           node.getClass().getSimpleName() + " - filter the graph with " +
                                           "MessageGraphFilter first");
    }
}
