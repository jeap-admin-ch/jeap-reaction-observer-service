package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

/**
 * A node that triggers a reaction or is the target of its action: a {@link Message}, an {@link HttpOperation} or a
 * {@link GenericInterface}. The kind of node is a hint for how to present it; the identity of an interface is its
 * observation type and FQN, i.e. its {@link #getId()}.
 */
public interface Interface extends Node {
}
