package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.ObservedReaction;

import java.time.LocalDate;
import java.util.List;

public interface ObservedReactionRepository {

    void saveAll(String idempotenceId, List<ObservedReaction> observedReactions);

    /**
     * Deletes observed reactions with an observation date before the given date, in batches of the given size that
     * each commit on their own, until none are left or {@code maxDeletions} have been deleted. Returns the number of
     * deleted observed reactions.
     */
    long deleteByObservationDateBefore(LocalDate date, int batchSize, int maxDeletions);
}
