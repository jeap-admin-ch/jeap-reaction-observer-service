package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.ObservedReaction;

import java.time.LocalDate;
import java.util.List;

public interface ObservedReactionRepository {

    void saveAll(String idempotenceId, List<ObservedReaction> observedReactions);

    /**
     * Deletes all observed reactions with an observation date before the given date, in batches that each commit
     * on their own, and returns the number of deleted observed reactions.
     */
    long deleteByObservationDateBefore(LocalDate date);
}
