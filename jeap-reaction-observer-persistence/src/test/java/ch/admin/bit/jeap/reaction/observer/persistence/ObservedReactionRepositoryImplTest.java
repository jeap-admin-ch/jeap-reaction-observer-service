package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.*;
import ch.admin.bit.jeap.reaction.observer.domain.models.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.models.ObservedReaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.Timeframe;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = {PersistenceAutoConfiguration.class,
        ObservedReactionRepositoryImplTest.MeterRegistryConfiguration.class})
@Slf4j
class ObservedReactionRepositoryImplTest extends PostgresTestContainerBase {

    /** A registry to read the metrics from, which the application context of the service would provide. */
    @TestConfiguration
    static class MeterRegistryConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private ObservedReactionRepository observedReactionRepository;

    @Autowired
    private JpaObservedReactionRepository jpaObservedReactionRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private MeterRegistry meterRegistry;

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
    void saveAll_whenAReactionIsNotKnown_thenTheKnownCountsAreSavedAndTheUnknownOneIsSkippedAndCounted() {
        // given: one known reaction
        String component = "componentWithUnknown";
        Reaction reaction = new Reaction("systemABC", component, "knownReaction",
                new Observation("t1", "event", "SomeEvent", Map.of()), List.of(), ZonedDateTime.now());
        reactionRepository.save(reaction);
        Timeframe timeframe = new Timeframe(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(5));
        ObservedReaction known = new ObservedReaction(component, "knownReaction", timeframe, 3);
        ObservedReaction unknown = new ObservedReaction(component, "unknownReaction", timeframe, 4);
        double skippedBefore = unknownReactionCounts();

        // when: the counts of the known and of an unknown reaction are saved together
        observedReactionRepository.saveAll("idempotenceWithUnknown", List.of(known, unknown));

        // then: the known count is saved, the unknown one is skipped and counted
        var found = StreamSupport.stream(jpaObservedReactionRepository.findAll().spliterator(), false)
                .filter(entity -> entity.getIdempotenceId().equals("idempotenceWithUnknown"))
                .map(entity -> entity.toDomainObject(reaction))
                .toList();
        assertThat(found).containsExactly(known);
        assertThat(unknownReactionCounts()).isEqualTo(skippedBefore + 1);
    }

    @Test
    void saveAll_whenNoReactionIsKnown_thenNothingIsSavedAndNothingThrown() {
        Timeframe timeframe = new Timeframe(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(5));
        double skippedBefore = unknownReactionCounts();

        observedReactionRepository.saveAll("idempotenceOnlyUnknown", List.of(
                new ObservedReaction("componentX", "unknown1", timeframe, 1),
                new ObservedReaction("componentX", "unknown2", timeframe, 2)));

        assertThat(jpaObservedReactionRepository.existsByIdempotenceId("idempotenceOnlyUnknown")).isFalse();
        assertThat(unknownReactionCounts()).isEqualTo(skippedBefore + 2);
    }

    private double unknownReactionCounts() {
        return meterRegistry.get("reaction_observer_service_unknown_reaction_counts").counter().count();
    }
}
