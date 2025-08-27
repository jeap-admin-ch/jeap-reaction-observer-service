package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

/**
 * Edge representing a trigger from a message node to a reaction node.
 * Includes the source message key, target reaction ID, and the daily median frequency of the trigger.
 */
public record Trigger(
        Interface source,
        Reaction target,
        Integer median
) implements Edge {
}
