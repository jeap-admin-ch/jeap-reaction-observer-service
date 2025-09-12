package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

import lombok.Builder;

import java.util.Set;

/**
 * Node representing a message in the graph.
 * Used to model events or commands within the system in different variants.
 */
@Builder
public record Message(
        long id,
        String messageType,
        String variant,
        SemanticType semantic
) implements Interface {
    @Override
    public long getId() {
        return id;
    }
}
