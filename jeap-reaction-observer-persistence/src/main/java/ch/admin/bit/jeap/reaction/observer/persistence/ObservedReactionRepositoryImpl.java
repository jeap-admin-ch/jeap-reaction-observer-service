package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReaction;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    public void deleteByTimeframeStartBefore(ZonedDateTime startOfDay) {
        jpaObservedReactionRepository.deleteByTimeframeStartBefore(startOfDay);
    }

    private ObservedReactionEntity toEntity(String idempotenceId, ObservedReaction observedReaction) {
        return ObservedReactionEntity.builder()
                .reactionFk(reactionFk(observedReaction.component(), observedReaction.reactionId()))
                .idempotenceId(idempotenceId)
                .timeframeStart(observedReaction.timeframe().start())
                .timeframeEnd(observedReaction.timeframe().end())
                .count(observedReaction.count())
                .build();
    }

    private Long reactionFk(String component, String reactionId) {
        return jpaReactionRepository.findIdByComponentAndReactionId(component, reactionId)
                .orElseThrow(() -> new IllegalStateException("Reaction not found: component=" + component + ", reactionId=" + reactionId));
    }
}
