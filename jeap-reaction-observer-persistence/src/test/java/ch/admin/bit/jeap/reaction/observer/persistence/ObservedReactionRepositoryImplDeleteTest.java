package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.models.ObservedReaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.Timeframe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getStartOfDay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * Runs without the transaction {@link DataJpaTest} wraps around each test: the delete runs outside any transaction
 * and commits every batch, so it neither sees rows a test transaction has not committed nor could they be rolled back.
 * The database is shared with the other test classes, so each test removes everything it committed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ObservedReactionRepositoryImplDeleteTest extends PostgresTestContainerBase {

    private static final String COMPONENT = "component123";

    @Autowired
    private ObservedReactionRepository observedReactionRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private JpaObservedReactionRepository jpaObservedReactionRepository;

    @AfterEach
    void deleteCommittedRows() {
        jdbcTemplate.execute("TRUNCATE observed_reaction, observed_reactions_aggregated, observation_property, action, " +
                "reaction, interface CASCADE");
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
        assertThat(findAll(reaction)).containsExactlyInAnyOrder(yesterdaysObservedReaction, todaysObservedReaction);
    }

    @Test
    void deleteByObservationDateBefore_deletesInBatchesUntilNothingIsLeft() {
        // given: more old observed reactions than fit into one batch, and one observed reaction of today
        String reactionId = "reactionId789";
        Reaction reaction = saveReaction(reactionId);
        saveObservedReactionsOfYesterday(reactionId, 5);
        ObservedReaction todaysObservedReaction = saveObservedReactionOfToday(reactionId);

        // when: deleting in batches of two
        long deleted = observedReactionRepository.deleteByObservationDateBefore(today(), 2, 500000);

        // then: all old observed reactions are deleted over several batches, today's observed reaction is kept
        assertEquals(5, deleted);
        assertThat(findAll(reaction)).containsExactly(todaysObservedReaction);
    }

    @Test
    void deleteByObservationDateBefore_stopsAtMaxDeletions() {
        // given: five old observed reactions and one observed reaction of today
        String reactionId = "reactionId012";
        Reaction reaction = saveReaction(reactionId);
        saveObservedReactionsOfYesterday(reactionId, 5);
        ObservedReaction todaysObservedReaction = saveObservedReactionOfToday(reactionId);

        // when: deleting in batches of two, at most three per run
        long deleted = observedReactionRepository.deleteByObservationDateBefore(today(), 2, 3);

        // then: three old observed reactions are deleted, the last batch is cut to the remaining one
        assertEquals(3, deleted);
        assertThat(findAll(reaction)).hasSize(3).contains(todaysObservedReaction);

        // when: the next run deletes the rest
        long deletedInNextRun = observedReactionRepository.deleteByObservationDateBefore(today(), 2, 3);

        // then
        assertEquals(2, deletedInNextRun);
        assertThat(findAll(reaction)).containsExactly(todaysObservedReaction);
    }

    @Test
    void deleteByObservationDateBefore_rejectsLimitsBelowOne() {
        LocalDate today = today();

        assertThrows(IllegalArgumentException.class, () -> observedReactionRepository.deleteByObservationDateBefore(today, 0, 500000));
        assertThrows(IllegalArgumentException.class, () -> observedReactionRepository.deleteByObservationDateBefore(today, 1000, 0));
    }

    @Test
    void deleteByObservationDateBefore_keepsCommittedBatchesWhenALaterBatchFails() {
        // given: four old observed reactions, and a failing second batch
        saveReaction("reactionIdBatchCommit");
        saveObservedReactionsOfYesterday("reactionIdBatchCommit", 4);
        failSecondBatchOfTwoWithAtMostThreeDeletions();

        // when
        assertThrows(IllegalStateException.class, () ->
                observedReactionRepository.deleteByObservationDateBefore(today(), 2, 3));

        // then: the first batch stays deleted
        assertEquals(2, jpaObservedReactionRepository.count());
    }

    @Test
    void deleteByObservationDateBefore_commitsEachBatchAlsoInsideACallersTransaction() {
        // given: four old observed reactions, and a failing second batch
        saveReaction("reactionIdCallerTransaction");
        saveObservedReactionsOfYesterday("reactionIdCallerTransaction", 4);
        failSecondBatchOfTwoWithAtMostThreeDeletions();

        // when: the caller's transaction rolls back because of the failing batch
        TransactionTemplate callersTransaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> callersTransaction.executeWithoutResult(status ->
                observedReactionRepository.deleteByObservationDateBefore(today(), 2, 3)));

        // then: the first batch did not join the caller's transaction and stays deleted
        assertEquals(2, jpaObservedReactionRepository.count());
    }

    private void failSecondBatchOfTwoWithAtMostThreeDeletions() {
        // with batches of two and at most three deletions, the second batch is the only one limited to one row
        doThrow(new IllegalStateException("Simulated failure of the second batch"))
                .when(jpaObservedReactionRepository).deleteBatchByObservationDateBefore(any(), eq(1));
    }

    private Reaction saveReaction(String reactionId) {
        Reaction reaction = new Reaction("systemABC", COMPONENT, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())),
                ZonedDateTime.now());
        reactionRepository.save(reaction);
        return reaction;
    }

    private void saveObservedReactionsOfYesterday(String reactionId, int count) {
        ZonedDateTime yesterday = getStartOfDay().minusDays(1);
        for (int i = 0; i < count; i++) {
            save(new ObservedReaction(COMPONENT, reactionId, new Timeframe(yesterday.plusMinutes(i), yesterday.plusMinutes(i + 1L)), 1));
        }
    }

    private ObservedReaction saveObservedReactionOfToday(String reactionId) {
        ObservedReaction observedReaction = new ObservedReaction(COMPONENT, reactionId, new Timeframe(getStartOfDay(), getStartOfDay().plusHours(1)), 5);
        save(observedReaction);
        return observedReaction;
    }

    private List<ObservedReaction> findAll(Reaction reaction) {
        return StreamSupport.stream(jpaObservedReactionRepository.findAll().spliterator(), false)
                .map(entity -> inDefaultZone(entity.toDomainObject(reaction)))
                .toList();
    }

    /**
     * Loaded from the database, the timestamps carry an offset instead of the zone they were saved with.
     */
    private static ObservedReaction inDefaultZone(ObservedReaction observedReaction) {
        ZoneId zone = ZoneId.systemDefault();
        Timeframe timeframe = observedReaction.timeframe();
        return new ObservedReaction(observedReaction.component(), observedReaction.reactionId(),
                new Timeframe(timeframe.start().withZoneSameInstant(zone), timeframe.end().withZoneSameInstant(zone)),
                observedReaction.count());
    }

    private void save(ObservedReaction observedReaction) {
        observedReactionRepository.saveAll(UUID.randomUUID().toString(), List.of(observedReaction));
    }

    private static LocalDate today() {
        return getStartOfDay().toLocalDate();
    }
}
