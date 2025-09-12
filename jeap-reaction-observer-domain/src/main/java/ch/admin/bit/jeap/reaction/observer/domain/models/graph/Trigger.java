package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

import lombok.Builder;

/**
 * Edge representing a trigger from a message node to a reaction node.
 * Includes the source message key, target reaction ID, and the daily median frequency of the trigger.
 */
@Builder
public record Trigger(
        Interface source,
        Reaction target,
        Integer median
) implements Edge {
}
