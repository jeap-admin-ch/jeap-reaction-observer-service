package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatistics;
import ch.admin.bit.jeap.reaction.observer.domain.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.aggregation.AggregationService;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionIdentifiedEventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionsObservedEventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ObservedReactionsAggregatedIT extends IntegrationTestBase {

    @Autowired
    AggregationService aggregationService;

    @Autowired
    ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;

    @Test
    void identified_and_observed_reaction_is_persisted() {
        // given: an identified reaction
        TestReaction testReaction = new TestReaction(
                TestObservation.ofEvent("MyEvent"), TestObservation.ofCommand("MyCommand"));

        // when: the identified reaction is notified to the reaction observer service
        ReactionIdentifiedEvent identifiedEvent = ReactionIdentifiedEventBuilder.buildEvent("system", "test1", testReaction);
        sendSync("reaction-identified", identifiedEvent);

        // then: the identified reaction is stored in the repository
        Reaction expectedReaction = testReaction.createExpectedReaction(identifiedEvent);
        await()
                .until(() -> reactionRepository.findByComponentAndReactionId(expectedReaction.component(), expectedReaction.reactionId()).isPresent());

        // given: a reaction is observed
        ReactionsObservedEvent observedEvent = createReactionsObservedEvent(testReaction);

        // when: the observed reactions are notified to the reaction observer service
        sendSync("reactions-observed", observedEvent);

        // then: the reaction observation is persisted
        String idempotenceId = observedEvent.getIdentity().getIdempotenceId();
        await()
                .until(() -> observedReactionIsPersisted(idempotenceId) == 1);

        aggregationService.aggregateData(getToday());

        List<ObservedReactionsAggregatedStatistics> statistics = observedReactionsAggregatedRepository.getStatistics("test1", LocalDate.now().minusDays(1L));
        Assertions.assertFalse(statistics.isEmpty());
        statistics.forEach(entry -> {
            if(entry.component().equals("test1")) {
                assertEquals(10, entry.count());
            }
        });
    }

    private static ReactionsObservedEvent createReactionsObservedEvent(TestReaction testReaction) {
        Instant now = Instant.now();
        return new ReactionsObservedEventBuilder("test1", "system")
                .serviceInstanceIdentifier(UUID.randomUUID())
                .countByReactionId(Map.of(testReaction.id(), 10))
                .timeframe(now.minusSeconds(300), now)
                .build();
    }

    private Integer observedReactionIsPersisted(String idempotenceId) {
        return jdbcTemplate.queryForObject("select count(*) from observed_reaction where idempotence_id=$1", Integer.class, idempotenceId);
    }
}
