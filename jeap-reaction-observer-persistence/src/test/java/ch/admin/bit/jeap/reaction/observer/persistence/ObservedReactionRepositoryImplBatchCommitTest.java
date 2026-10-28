package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.models.ObservedReaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.Timeframe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getStartOfDay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * Runs without the transaction {@link DataJpaTest} wraps around each test, so every batch commits as it does in
 * production. The spy gives this class its own application context and thereby its own in-memory database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ObservedReactionRepositoryImplBatchCommitTest {

    private static final String COMPONENT = "component123";

    @Autowired
    private ObservedReactionRepository observedReactionRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @MockitoSpyBean
    private JpaObservedReactionRepository jpaObservedReactionRepository;

    @Test
    void deleteByObservationDateBefore_keepsCommittedBatchesWhenALaterBatchFails() {
        // given: four old observed reactions
        String reactionId = "reactionIdBatchCommit";
        reactionRepository.save(new Reaction("systemABC", COMPONENT, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())),
                ZonedDateTime.now()));
        ZonedDateTime yesterday = getStartOfDay().minusDays(1);
        for (int i = 0; i < 4; i++) {
            observedReactionRepository.saveAll(UUID.randomUUID().toString(), List.of(
                    new ObservedReaction(COMPONENT, reactionId, new Timeframe(yesterday.plusMinutes(i), yesterday.plusMinutes(i + 1L)), 1)));
        }
        // with batches of two and at most three deletions, the second batch is the only one limited to one row
        doThrow(new IllegalStateException("Simulated failure of the second batch"))
                .when(jpaObservedReactionRepository).deleteBatchByObservationDateBefore(any(), eq(1));

        // when
        assertThrows(IllegalStateException.class, () ->
                observedReactionRepository.deleteByObservationDateBefore(getStartOfDay().toLocalDate(), 2, 3));

        // then: the first batch stays deleted
        assertEquals(2, jpaObservedReactionRepository.count());
    }
}
