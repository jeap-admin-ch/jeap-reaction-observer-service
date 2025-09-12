package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatistics;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionGraphRepository;
import ch.admin.bit.jeap.reaction.observer.domain.aggregation.AggregationService;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Trigger;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionIdentifiedV2EventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionsObservedEventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;
import ch.admin.bit.jeap.reaction.observer.web.service.ScheduledTasksService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class AllReactionsGraphIT extends IntegrationTestBase {

    @Autowired
    AggregationService aggregationService;

    @Autowired
    ScheduledTasksService scheduledTasksService;

    @Autowired
    GraphHolder graphHolder;

    /**
     * command1 -> service1 (1) -> event1 -> service2 (1) -> command3
     *                                    -> service3 (2) -> command4
     *                          -> command2
     */
    @Test
    void identified_and_observed_reaction_is_persisted() {
        TestObservation command1 = TestObservation.ofEvent("Command1");
        TestObservation command2 = TestObservation.ofEvent("Command2");
        TestObservation event1 = TestObservation.ofEvent("Event1");

        // given: identified reactions
        TestReaction testReaction1 = new TestReaction(command1, List.of(command2, event1), "reaction1");
        sendAndAwaitReactionPersistence(testReaction1, "system1", "service1");
        sendAndAwaitObservedEventForReaction(testReaction1, "system1", "service1", 5);

        TestObservation command3 = TestObservation.ofEvent("Command3");
        TestReaction testReaction2 = new TestReaction(event1, List.of(command3), "reaction2");
        sendAndAwaitReactionPersistence(testReaction2, "system1", "service2");
        sendAndAwaitObservedEventForReaction(testReaction2, "system1", "service2", 7);

        TestObservation command4 = TestObservation.ofEvent("Command4");
        TestReaction testReaction3 = new TestReaction(event1, List.of(command4), "reaction3");
        sendAndAwaitReactionPersistence(testReaction3, "system2", "service3");
        sendAndAwaitObservedEventForReaction(testReaction3, "system2", "service3", 200);

        // when: aggregate data
        aggregationService.aggregateData(getToday());

        // and: manually trigger the scheduled graph refresh
        scheduledTasksService.scheduledRefreshReactionGraph();


        // then: the graph was built and stored
        Graph graph = graphHolder.getGraph();
        assertNotNull(graph);
        assertFalse(graph.nodes().isEmpty());
        assertFalse(graph.edges().isEmpty());
    }

    private void sendAndAwaitReactionPersistence(TestReaction testReaction, String system, String component) {
        // Build the event
        ReactionIdentifiedEvent identifiedEvent = ReactionIdentifiedV2EventBuilder.buildEvent(system, component, testReaction);

        // Send the event
        sendSync("reaction-identified", identifiedEvent);

        // Create expected domain reaction
        Reaction expectedReaction = testReaction.createExpectedReaction(identifiedEvent);

        // Await persistence
        await().until(() ->
                reactionRepository.findByComponentAndReactionId(expectedReaction.component(), expectedReaction.reactionId()).isPresent()
        );
    }

    private void sendAndAwaitObservedEventForReaction(TestReaction testReaction, String system, String component, int count) {
        // given: a reaction is observed
        ReactionsObservedEvent observedEvent = createReactionsObservedEvent(testReaction, system, component, count);

        // when: the observed reactions are notified to the reaction observer service
        sendSync("reactions-observed", observedEvent);

        // then: the reaction observation is persisted
        String idempotenceId = observedEvent.getIdentity().getIdempotenceId();
        await()
                .until(() -> observedReactionIsPersisted(idempotenceId) == 1);
    }


    private static ReactionsObservedEvent createReactionsObservedEvent(TestReaction testReaction, String system, String component, int count) {
        Instant now = Instant.now();
        return new ReactionsObservedEventBuilder(component, system)
                .serviceInstanceIdentifier(UUID.randomUUID())
                .countByReactionId(Map.of(testReaction.id(), count))
                .timeframe(now.minusSeconds(300), now)
                .build();
    }

    private Integer observedReactionIsPersisted(String idempotenceId) {
        return jdbcTemplate.queryForObject("select count(*) from observed_reaction where idempotence_id=$1", Integer.class, idempotenceId);
    }
}