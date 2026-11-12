package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

/**
 * A node that triggers a reaction or is the target of its action: a {@link Message}, an {@link HttpOperation} or a
 * {@link GenericInterface}. The kind of node is a hint for how to present it and is not part of the identity.
 * <p>
 * The business id of an interface is its observation type and FQN ({@link #type()} and {@link #fqn()}). Its
 * {@link #id()} is an additional technical id.
 */
public interface Interface extends Node {

    /** The observation type of the interface as it was reported, e.g. {@code event} or {@code http_request}. */
    String type();

    /** The fully qualified name of the interface as it was reported. */
    String fqn();
}
