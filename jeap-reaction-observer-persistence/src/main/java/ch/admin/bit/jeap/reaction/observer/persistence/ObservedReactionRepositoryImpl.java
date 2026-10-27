package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.models.ObservedReaction;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

class ObservedReactionRepositoryImpl implements ObservedReactionRepository {

    static final int DELETE_BATCH_SIZE = 10_000;

    private final JpaObservedReactionRepository jpaObservedReactionRepository;
    private final JpaReactionRepository jpaReactionRepository;
    private final int deleteBatchSize;

    ObservedReactionRepositoryImpl(JpaObservedReactionRepository jpaObservedReactionRepository, JpaReactionRepository jpaReactionRepository) {
        this(jpaObservedReactionRepository, jpaReactionRepository, DELETE_BATCH_SIZE);
    }

    ObservedReactionRepositoryImpl(JpaObservedReactionRepository jpaObservedReactionRepository, JpaReactionRepository jpaReactionRepository, int deleteBatchSize) {
        this.jpaObservedReactionRepository = jpaObservedReactionRepository;
        this.jpaReactionRepository = jpaReactionRepository;
        this.deleteBatchSize = deleteBatchSize;
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
    public long deleteByObservationDateBefore(LocalDate date) {
        long deleted = 0;
        int deletedInBatch;
        do {
            deletedInBatch = jpaObservedReactionRepository.deleteBatchByObservationDateBefore(date, deleteBatchSize);
            deleted += deletedInBatch;
        } while (deletedInBatch == deleteBatchSize);
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
