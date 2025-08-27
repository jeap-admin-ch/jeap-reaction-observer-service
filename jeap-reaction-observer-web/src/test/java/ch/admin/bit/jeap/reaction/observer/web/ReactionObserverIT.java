package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionIdentifiedV2EventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionsObservedEventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

class ReactionObserverIT extends IntegrationTestBase {

    @Test
    void identified_and_observed_reaction_is_persisted() {
        // given: an identified reaction
        TestReaction testReaction = new TestReaction(
                TestObservation.ofEvent("MyEvent"), List.of(TestObservation.ofCommand("MyCommand")), "reaction1");

        // when: the identified reaction is notified to the reaction observer service
        ReactionIdentifiedEvent identifiedEvent = ReactionIdentifiedV2EventBuilder.buildEvent("system", "test", testReaction);
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
    }

    private static ReactionsObservedEvent createReactionsObservedEvent(TestReaction testReaction) {
        Instant now = Instant.now();
        return new ReactionsObservedEventBuilder("test", "system")
                .serviceInstanceIdentifier(UUID.randomUUID())
                .countByReactionId(Map.of(testReaction.id(), 10))
                .timeframe(now.minusSeconds(300), now)
                .build();
    }

    private Integer observedReactionIsPersisted(String idempotenceId) {
        return jdbcTemplate.queryForObject("select count(*) from observed_reaction where idempotence_id=$1", Integer.class, idempotenceId);
    }
}
