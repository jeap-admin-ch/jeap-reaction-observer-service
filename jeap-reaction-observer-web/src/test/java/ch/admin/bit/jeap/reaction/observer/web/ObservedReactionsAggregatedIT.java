package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatistics;
import ch.admin.bit.jeap.reaction.observer.domain.aggregation.AggregationService;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionIdentifiedV2EventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

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
                TestObservation.ofEvent("MyEvent"), List.of(TestObservation.ofCommand("MyCommand")), "reaction1");

        // when: the identified reaction is notified to the reaction observer service
        ReactionIdentifiedEvent identifiedEvent = ReactionIdentifiedV2EventBuilder.buildEvent("system", "test1", testReaction);
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
            if (entry.component().equals("test1")) {
                assertEquals(10, entry.count());
            }
        });
    }
}
