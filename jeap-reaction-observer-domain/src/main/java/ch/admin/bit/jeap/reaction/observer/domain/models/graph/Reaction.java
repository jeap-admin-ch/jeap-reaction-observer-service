package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

import lombok.Builder;

/**
 * This node representing a reaction in the graph.
 * Identified by a reaction ID and associated component.
 * Reactions are triggered by messages over a TriggerEdge.
 *
 * @param median the median of the daily observation counts within the statistics window, or null when none
 *               were aggregated. It is counted per reaction, which is why it is here and not only on the
 *               trigger edge: a reaction that no message triggered has no edge to carry it.
 */
@Builder
public record Reaction(
        long id,
        String component,
        String system,
        Integer median
) implements Node {
    @Override
    public long getId() {
        return id;
    }
}
