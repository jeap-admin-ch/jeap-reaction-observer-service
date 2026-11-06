package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

import lombok.Builder;

/**
 * Node representing an interface the observer cannot interpret: an observation type it does not know, or an FQN
 * of a known type that could not be parsed. It carries the observation type and FQN as they were reported, so that
 * the graph stays complete instead of failing.
 */
@Builder
public record GenericInterface(
        long id,
        String type,
        String fqn
) implements Interface {
}
