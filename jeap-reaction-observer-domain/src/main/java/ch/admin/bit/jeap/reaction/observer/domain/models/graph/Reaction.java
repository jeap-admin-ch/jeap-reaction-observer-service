package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

import java.util.Set;

/**
 * This node representing a reaction in the graph.
 * Identified by a reaction ID and associated component.
 * Reactions are triggered by messages over a TriggerEdge.
 */
public record Reaction(
        long id,
        String component,
        Trigger trigger,
        Set<Action> actions
) implements Node {
    @Override
    public long getId() {
        return id;
    }
}