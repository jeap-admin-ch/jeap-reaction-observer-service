package ch.admin.bit.jeap.reaction.observer.domain;

import java.util.Optional;

public interface ReactionRepository {

    /**
     * Saves an identified reaction to the repository (idempotent operation)
     */
    void save(Reaction reaction);

    Optional<Reaction> findByComponentAndReactionId(String component, String reactionId);
}
