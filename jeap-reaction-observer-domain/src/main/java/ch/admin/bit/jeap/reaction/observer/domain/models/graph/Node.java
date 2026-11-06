package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

/**
 * A node of the graph, identified by {@link #id()} - the accessor every implementation is a record for, so that
 * {@link #getId()} is satisfied without each of them repeating the same bridge method.
 */
public interface Node {
    long id();

    default long getId() {
        return id();
    }
}
