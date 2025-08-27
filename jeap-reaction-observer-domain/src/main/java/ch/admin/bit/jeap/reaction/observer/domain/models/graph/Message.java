package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

import java.util.Set;

/**
 * Node representing a message in the graph.
 * Used to model events or commands within the system in different variants.
 */
public record Message(
        long id,
        String messageKey,
        String messageType,
        String variant,
        SemanticType semantic,
        Set<Trigger> triggers,
        Set<Action> action
) implements Interface {
    @Override
    public long getId() {
        return id;
    }
}
