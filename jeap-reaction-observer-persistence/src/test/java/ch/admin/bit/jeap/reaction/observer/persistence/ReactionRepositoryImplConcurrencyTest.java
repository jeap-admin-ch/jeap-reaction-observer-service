package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

/**
 * Runs without the transaction {@link DataJpaTest} wraps around each test: the concurrent identifications have to run
 * in transactions of their own, as they only collide when they commit.
 * The database is shared with the other test classes, so the test removes everything it committed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReactionRepositoryImplConcurrencyTest extends PostgresTestContainerBase {

    private static final String COMPONENT = "concurrentComponent";
    private static final String REACTION_ID = "concurrentReaction";
    private static final int TIMEOUT_SECONDS = 60;

    @Autowired
    private ReactionRepository reactionRepository;

    @MockitoSpyBean
    private JpaReactionRepository jpaReactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void deleteCommittedRows() {
        jdbcTemplate.execute("TRUNCATE observed_reaction, observed_reactions_aggregated, observation_property, action, " +
                "reaction, interface CASCADE");
    }

    @Test
    void save_whenAReactionIsIdentifiedConcurrentlyForTheFirstTime_thenOneCreatesItAndTheOtherFailsAndCanBeRepeated() throws Exception {
        // given: two identifications that both find the reaction unknown before either of them creates it
        letTwoIdentificationsFindTheReactionUnknownFirst();

        // when: they run concurrently
        List<Throwable> failures = saveConcurrently(reaction(1));

        // then: there is exactly one reaction, and the identification that lost fails
        assertThat(failures).singleElement().isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from reaction where component = ?", Integer.class, COMPONENT))
                .isEqualTo(1);

        // when: the identification that lost is repeated
        reactionRepository.save(reaction(2));

        // then: it finds the reaction and records its reaction model version
        assertThat(jdbcTemplate.queryForObject("select count(*) from reaction where component = ?", Integer.class, COMPONENT))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select last_identified_by_model_version from reaction where component = ?", Integer.class, COMPONENT))
                .isEqualTo(2);
    }

    private void letTwoIdentificationsFindTheReactionUnknownFirst() {
        CyclicBarrier bothUpdatedNothing = new CyclicBarrier(2);
        Answer<?> realMethod = mockingDetails(jpaReactionRepository).getMockCreationSettings().getDefaultAnswer();
        Answer<Object> realMethodThenWait = invocation -> {
            Object updatedReactions = realMethod.answer(invocation);
            bothUpdatedNothing.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return updatedReactions;
        };
        doAnswer(realMethodThenWait).doAnswer(realMethodThenWait).doAnswer(realMethod)
                .when(jpaReactionRepository).updateLastIdentifiedByModelVersion(anyString(), anyString(), anyInt());
    }

    private List<Throwable> saveConcurrently(Reaction reaction) throws InterruptedException, TimeoutException {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> saves = List.of(
                    executor.submit(() -> reactionRepository.save(reaction)),
                    executor.submit(() -> reactionRepository.save(reaction)));
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> save : saves) {
                try {
                    save.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        }
    }

    private static Reaction reaction(int reactionModelVersion) {
        return new Reaction("system0", COMPONENT, REACTION_ID,
                new Observation("t1", "event", "SomeEvent", Map.of()),
                List.of(new Observation("a1", "command", "SomeCommand", Map.of())),
                ZonedDateTime.now(), reactionModelVersion);
    }
}
