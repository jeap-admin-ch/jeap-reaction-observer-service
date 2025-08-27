package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getStartOfDay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Slf4j
class ObservedReactionRepositoryImplTest {

    @Autowired
    private ObservedReactionRepository observedReactionRepository;

    @Autowired
    private JpaObservedReactionRepository jpaObservedReactionRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Test
    void saveAll_noDuplicates() {
        // given: a reaction and an observation of the reaction
        String reactionId = "reactionId123";
        String system = "systemABC";
        String component = "component123";
        String idempotenceId = "idempotence123";
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())),
                ZonedDateTime.now());
        reactionRepository.save(reaction);
        int count = 5;
        ObservedReaction observedReaction = new ObservedReaction(component, reactionId,
                new Timeframe(ZonedDateTime.now(), ZonedDateTime.now().plusHours(1)), count);

        // when: the observed reaction is saved (twice)
        observedReactionRepository.saveAll(idempotenceId, List.of(observedReaction));
        observedReactionRepository.saveAll(idempotenceId, List.of(observedReaction));

        // then: the observed reaction is stored once in the repository and can be retrieved
        var foundObservedReactions = StreamSupport.stream(jpaObservedReactionRepository.findAll().spliterator(), false)
                .filter(entity -> entity.getIdempotenceId().equals(idempotenceId))
                .map(entity -> entity.toDomainObject(reaction))
                .toList();

        assertThat(foundObservedReactions)
                .containsExactly(observedReaction);
    }

    @Test
    void deleteByTimeframeStart() {
        // given: a reaction and several observations over different periods
        String reactionId = "reactionId456";
        String system = "systemABC";
        String component = "component123";
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())),
                ZonedDateTime.now());
        reactionRepository.save(reaction);
        ZonedDateTime yesterday = getStartOfDay().minusDays(1);
        ZonedDateTime theDayBeforeYesterday = yesterday.minusDays(1);

        save(new ObservedReaction(component, reactionId, new Timeframe(theDayBeforeYesterday, theDayBeforeYesterday.plusHours(1)), 1));
        ObservedReaction yesterdaysObservedReaction = new ObservedReaction(component, reactionId, new Timeframe(yesterday, yesterday.plusHours(1)), 5);
        save(yesterdaysObservedReaction);
        ObservedReaction todaysObservedReaction = new ObservedReaction(component, reactionId, new Timeframe(getStartOfDay(), getStartOfDay().plusHours(1)), 5);
        save(todaysObservedReaction);

        // when
        observedReactionRepository.deleteByTimeframeStartBefore(yesterday);

        // then: observed reactions before yesterday are deleted
        var foundObservedReactions = StreamSupport.stream(jpaObservedReactionRepository.findAll().spliterator(), false)
                .map(entity -> entity.toDomainObject(reaction))
                .toList();

        assertEquals(2, foundObservedReactions.size());
        assertThat(foundObservedReactions).containsExactly(yesterdaysObservedReaction, todaysObservedReaction);
    }

    private void save(ObservedReaction observedReaction) {
        observedReactionRepository.saveAll(UUID.randomUUID().toString(), List.of(observedReaction));
    }

}
