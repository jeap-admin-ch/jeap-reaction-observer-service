package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

/**
 * Edge representing an action from a reaction node to an interface node.
 * Connects a reaction (source) to an interface (target).
 * Used to model process outputs or follow-up events.
 */
public record Action(
        Reaction source,
        Interface target
) implements Edge {
}
