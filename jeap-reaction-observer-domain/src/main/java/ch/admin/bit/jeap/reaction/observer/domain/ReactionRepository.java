package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;

import java.util.Optional;

public interface ReactionRepository {

    /**
     * Saves an identified reaction to the repository (idempotent operation). A reaction that is already known keeps
     * its structure and identification timestamp, only its reaction model version is replaced by the reported one.
     * <p>
     * When a reaction is identified for the first time concurrently, one caller creates it and the others fail when
     * their transaction commits. Repeating a failed call finds the reaction.
     */
    void save(Reaction reaction);

    Optional<Reaction> findByComponentAndReactionId(String component, String reactionId);
}
