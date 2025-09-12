package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

import lombok.Builder;

/**
 * Edge representing an action from a reaction node to an interface node.
 * Connects a reaction (source) to an interface (target).
 * Used to model process outputs or follow-up events.
 */
@Builder
public record Action(
        Reaction source,
        Interface target
) implements Edge {
}
