package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.models.ObservedReaction;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

class ObservedReactionRepositoryImpl implements ObservedReactionRepository {

    private final JpaObservedReactionRepository jpaObservedReactionRepository;
    private final JpaReactionRepository jpaReactionRepository;

    ObservedReactionRepositoryImpl(JpaObservedReactionRepository jpaObservedReactionRepository, JpaReactionRepository jpaReactionRepository) {
        this.jpaObservedReactionRepository = jpaObservedReactionRepository;
        this.jpaReactionRepository = jpaReactionRepository;
    }

    @Override
    @Transactional
    public void saveAll(String idempotenceId, List<ObservedReaction> observedReactions) {
        List<ObservedReactionEntity> entities = observedReactions.stream()
                .map(observedReaction -> toEntity(idempotenceId, observedReaction))
                .toList();

        if (!jpaObservedReactionRepository.existsByIdempotenceId(idempotenceId)) {
            jpaObservedReactionRepository.saveAll(entities);
        }
    }

    /**
     * Not transactional on purpose: each batch commits on its own, so every statement stays short and the rows
     * deleted so far stay deleted if a later batch fails. A single delete over the whole backlog can run into
     * a timeout and roll back completely, and the backlog then keeps growing.
     */
    @Override
    public long deleteByObservationDateBefore(LocalDate date, int batchSize, int maxDeletions) {
        // A batch size of 0 would make every batch "complete" and loop forever
        if (batchSize < 1 || maxDeletions < 1) {
            throw new IllegalArgumentException("batchSize and maxDeletions must be at least 1, got %d and %d"
                    .formatted(batchSize, maxDeletions));
        }
        long deleted = 0;
        int limit;
        int deletedInBatch;
        do {
            limit = (int) Math.min(batchSize, maxDeletions - deleted);
            deletedInBatch = jpaObservedReactionRepository.deleteBatchByObservationDateBefore(date, limit);
            deleted += deletedInBatch;
        } while (deletedInBatch == limit && deleted < maxDeletions);
        return deleted;
    }

    private ObservedReactionEntity toEntity(String idempotenceId, ObservedReaction observedReaction) {
        ZonedDateTime start = observedReaction.timeframe().start();
        return ObservedReactionEntity.builder()
                .reactionFk(reactionFk(observedReaction.component(), observedReaction.reactionId()))
                .idempotenceId(idempotenceId)
                .timeframeStart(start)
                .timeframeEnd(observedReaction.timeframe().end())
                .observationDate(start.toLocalDate())
                .count(observedReaction.count())
                .build();
    }

    private Long reactionFk(String component, String reactionId) {
        return jpaReactionRepository.findIdByComponentAndReactionId(component, reactionId)
                .orElseThrow(() -> new IllegalStateException("Reaction not found: component=" + component + ", reactionId=" + reactionId));
    }
}
