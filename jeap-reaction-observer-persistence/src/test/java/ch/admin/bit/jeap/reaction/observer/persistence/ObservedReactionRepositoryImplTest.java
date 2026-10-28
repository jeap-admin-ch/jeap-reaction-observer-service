package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.*;
import ch.admin.bit.jeap.reaction.observer.domain.models.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.models.ObservedReaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.Timeframe;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getStartOfDay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Slf4j
class ObservedReactionRepositoryImplTest {

    private static final String COMPONENT = "component123";

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
    void deleteByObservationDateBefore() {
        // given: a reaction and several observations over different periods
        String reactionId = "reactionId456";
        Reaction reaction = saveReaction(reactionId);
        ZonedDateTime yesterday = getStartOfDay().minusDays(1);
        ZonedDateTime theDayBeforeYesterday = yesterday.minusDays(1);

        save(new ObservedReaction(COMPONENT, reactionId, new Timeframe(theDayBeforeYesterday, theDayBeforeYesterday.plusHours(1)), 1));
        ObservedReaction yesterdaysObservedReaction = new ObservedReaction(COMPONENT, reactionId, new Timeframe(yesterday, yesterday.plusHours(1)), 5);
        save(yesterdaysObservedReaction);
        ObservedReaction todaysObservedReaction = new ObservedReaction(COMPONENT, reactionId, new Timeframe(getStartOfDay(), getStartOfDay().plusHours(1)), 5);
        save(todaysObservedReaction);

        // when
        long deleted = observedReactionRepository.deleteByObservationDateBefore(yesterday.toLocalDate(), 1000, 500000);

        // then: observed reactions before yesterday are deleted
        assertEquals(1, deleted);
        assertThat(findAll(reaction)).containsExactly(yesterdaysObservedReaction, todaysObservedReaction);
    }

    @Test
    void deleteByObservationDateBefore_deletesInBatchesUntilNothingIsLeft() {
        // given: more old observed reactions than fit into one batch, and one observed reaction of today
        String reactionId = "reactionId789";
        Reaction reaction = saveReaction(reactionId);
        ZonedDateTime yesterday = getStartOfDay().minusDays(1);
        for (int i = 0; i < 5; i++) {
            save(new ObservedReaction(COMPONENT, reactionId, new Timeframe(yesterday.plusMinutes(i), yesterday.plusMinutes(i + 1L)), 1));
        }
        ObservedReaction todaysObservedReaction = new ObservedReaction(COMPONENT, reactionId, new Timeframe(getStartOfDay(), getStartOfDay().plusHours(1)), 5);
        save(todaysObservedReaction);

        // when: deleting in batches of two
        long deleted = observedReactionRepository.deleteByObservationDateBefore(getStartOfDay().toLocalDate(), 2, 500000);

        // then: all old observed reactions are deleted over several batches, today's observed reaction is kept
        assertEquals(5, deleted);
        assertThat(findAll(reaction)).containsExactly(todaysObservedReaction);
    }

    @Test
    void deleteByObservationDateBefore_stopsAtMaxDeletions() {
        // given: five old observed reactions and one observed reaction of today
        String reactionId = "reactionId012";
        Reaction reaction = saveReaction(reactionId);
        ZonedDateTime yesterday = getStartOfDay().minusDays(1);
        for (int i = 0; i < 5; i++) {
            save(new ObservedReaction(COMPONENT, reactionId, new Timeframe(yesterday.plusMinutes(i), yesterday.plusMinutes(i + 1L)), 1));
        }
        ObservedReaction todaysObservedReaction = new ObservedReaction(COMPONENT, reactionId, new Timeframe(getStartOfDay(), getStartOfDay().plusHours(1)), 5);
        save(todaysObservedReaction);

        // when: deleting in batches of two, at most three per run
        long deleted = observedReactionRepository.deleteByObservationDateBefore(getStartOfDay().toLocalDate(), 2, 3);

        // then: three old observed reactions are deleted, the last batch is cut to the remaining one
        assertEquals(3, deleted);
        assertThat(findAll(reaction)).hasSize(3).contains(todaysObservedReaction);

        // when: the next run deletes the rest
        long deletedInNextRun = observedReactionRepository.deleteByObservationDateBefore(getStartOfDay().toLocalDate(), 2, 3);

        // then
        assertEquals(2, deletedInNextRun);
        assertThat(findAll(reaction)).containsExactly(todaysObservedReaction);
    }

    @Test
    void deleteByObservationDateBefore_rejectsLimitsBelowOne() {
        LocalDate today = getStartOfDay().toLocalDate();

        assertThrows(IllegalArgumentException.class, () -> observedReactionRepository.deleteByObservationDateBefore(today, 0, 500000));
        assertThrows(IllegalArgumentException.class, () -> observedReactionRepository.deleteByObservationDateBefore(today, 1000, 0));
    }

    private Reaction saveReaction(String reactionId) {
        Reaction reaction = new Reaction("systemABC", COMPONENT, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())),
                ZonedDateTime.now());
        reactionRepository.save(reaction);
        return reaction;
    }

    private List<ObservedReaction> findAll(Reaction reaction) {
        return StreamSupport.stream(jpaObservedReactionRepository.findAll().spliterator(), false)
                .map(entity -> entity.toDomainObject(reaction))
                .toList();
    }

    private void save(ObservedReaction observedReaction) {
        observedReactionRepository.saveAll(UUID.randomUUID().toString(), List.of(observedReaction));
    }

}
