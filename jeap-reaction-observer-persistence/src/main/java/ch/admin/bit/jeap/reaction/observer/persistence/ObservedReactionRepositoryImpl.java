package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionObserverMetrics;
import ch.admin.bit.jeap.reaction.observer.domain.models.ObservedReaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
class ObservedReactionRepositoryImpl implements ObservedReactionRepository {

    /** How many unknown reaction ids a log line names at most, the number of them is always logged. */
    private static final int MAX_LOGGED_UNKNOWN_REACTION_IDS = 10;

    private final JpaObservedReactionRepository jpaObservedReactionRepository;
    private final JpaReactionRepository jpaReactionRepository;
    private final ReactionObserverMetrics metrics;

    ObservedReactionRepositoryImpl(JpaObservedReactionRepository jpaObservedReactionRepository,
                                   JpaReactionRepository jpaReactionRepository,
                                   ReactionObserverMetrics metrics) {
        this.jpaObservedReactionRepository = jpaObservedReactionRepository;
        this.jpaReactionRepository = jpaReactionRepository;
        this.metrics = metrics;
    }

    /**
     * Saves the counts of reactions that are known, and skips the counts of reactions that are not.
     * <p>
     * A count of a reaction that has not been identified (yet) is skipped and counted in
     * {@link ReactionObserverMetrics#unknownReactionCountsSkipped} instead of failing the whole batch: one unknown
     * reaction id would otherwise make every count of the instance fail, every time it reports, until it restarts.
     * The price is that counts arriving before the identification of their reaction are lost.
     */
    @Override
    @Transactional
    public void saveAll(String idempotenceId, List<ObservedReaction> observedReactions) {
        if (jpaObservedReactionRepository.existsByIdempotenceId(idempotenceId)) {
            return;
        }

        Map<String, Map<String, Long>> fksByComponent = reactionFksByComponent(observedReactions);
        List<ObservedReactionEntity> entities = new ArrayList<>();
        List<ObservedReaction> unknown = new ArrayList<>();
        for (ObservedReaction observedReaction : observedReactions) {
            Long reactionFk = fksByComponent.getOrDefault(observedReaction.component(), Map.of())
                    .get(observedReaction.reactionId());
            if (reactionFk == null) {
                unknown.add(observedReaction);
            } else {
                entities.add(toEntity(idempotenceId, reactionFk, observedReaction));
            }
        }

        if (!unknown.isEmpty()) {
            log.warn("Skipped {} of {} observed reaction counts ({}) because their reactions are not known, " +
                     "e.g. component={}, reactionIds={}", unknown.size(), observedReactions.size(), idempotenceId,
                    unknown.getFirst().component(), unknown.stream()
                            .limit(MAX_LOGGED_UNKNOWN_REACTION_IDS)
                            .map(ObservedReaction::reactionId)
                            .toList());
            metrics.unknownReactionCountsSkipped(unknown.size());
        }
        jpaObservedReactionRepository.saveAll(entities);
    }

    /**
     * Runs outside any transaction on purpose, also when the caller has one: each batch commits on its own, so every
     * statement stays short and the rows deleted so far stay deleted if a later batch fails. A single delete over the
     * whole backlog can run into a timeout and roll back completely, and the backlog then keeps growing.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

    private static ObservedReactionEntity toEntity(String idempotenceId, Long reactionFk,
                                                   ObservedReaction observedReaction) {
        ZonedDateTime start = observedReaction.timeframe().start();
        return ObservedReactionEntity.builder()
                .reactionFk(reactionFk)
                .idempotenceId(idempotenceId)
                .timeframeStart(start)
                .timeframeEnd(observedReaction.timeframe().end())
                .observationDate(start.toLocalDate())
                .count(observedReaction.count())
                .build();
    }

    /**
     * The ids of the known reactions, per component and reaction id - with one query per component rather than one
     * per count. A reaction id without an entry is not known.
     * <p>
     * Grouped by component because the same reaction id string is only unique within a component: nothing in the
     * {@link ObservedReactionRepository#saveAll} contract guarantees a single component per call, even though the
     * only caller today, {@code ReactionsObservedEventListener}, always passes counts of one component - one
     * {@code ReactionsObservedEvent} is published by one service.
     */
    private Map<String, Map<String, Long>> reactionFksByComponent(List<ObservedReaction> observedReactions) {
        Map<String, List<String>> reactionIdsByComponent = observedReactions.stream()
                .collect(Collectors.groupingBy(ObservedReaction::component,
                        Collectors.mapping(ObservedReaction::reactionId, Collectors.toList())));
        Map<String, Map<String, Long>> fksByComponent = new HashMap<>();
        reactionIdsByComponent.forEach((component, reactionIds) -> fksByComponent.put(component,
                jpaReactionRepository.findFksByComponentAndReactionIdIn(component, reactionIds).stream()
                        .collect(Collectors.toMap(JpaReactionRepository.ReactionFk::getReactionId,
                                JpaReactionRepository.ReactionFk::getId))));
        return fksByComponent;
    }
}
