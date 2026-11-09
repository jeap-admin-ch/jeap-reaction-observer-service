package ch.admin.bit.jeap.reaction.observer.kafka;

import ch.admin.bit.jeap.messaging.annotations.JeapMessageConsumerContract;
import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionGraphRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionIdentifiedV2EventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;

import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@JeapMessageConsumerContract(value = ReactionIdentifiedEvent.TypeRef.class,
        appName = "test", topic = "reaction-identified")
@SpringBootTest
class ReactionIdentifiedEventListenerTest extends KafkaIntegrationTestBase {

    @MockitoBean
    private ReactionRepository reactionRepository;

    @MockitoBean
    private ObservedReactionRepository observedReactionRepository;

    @MockitoBean
    private ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;

    @MockitoBean
    private ReactionGraphRepository reactionGraphRepository;

    @Test
    void onReactionIdentifiedEvent_takesTheReactionModelVersionFromTheEvent() {
        // given: a reaction identified with a reaction model version
        TestReaction testReaction = new TestReaction(
                TestObservation.ofEvent("MyEvent"), singletonList(TestObservation.ofCommand("MyCommand")), "reactionWithVersion");
        ReactionIdentifiedEvent event = ReactionIdentifiedV2EventBuilder.buildEvent("test", "test", testReaction, 2);

        // when: the identified reaction is notified to the reaction observer service
        sendSync("reaction-identified", event);

        // then: the reaction is stored with the reaction model version of the event
        await().untilAsserted(() -> verify(reactionRepository).save(argThat(reaction ->
                reaction.reactionId().equals("reactionWithVersion") && reaction.reactionModelVersion() == 2)));
    }

    @Test
    void onReactionIdentifiedEvent_withoutReactionModelVersion_isModelVersion1() {
        // given: a reaction identified without a reaction model version
        TestReaction testReaction = new TestReaction(
                TestObservation.ofEvent("MyEvent"), singletonList(TestObservation.ofCommand("MyCommand")), "reactionWithoutVersion");
        ReactionIdentifiedEvent event = ReactionIdentifiedV2EventBuilder.buildEvent("test", "test", testReaction);

        // when: the identified reaction is notified to the reaction observer service
        sendSync("reaction-identified", event);

        // then: the reaction is stored with reaction model version 1
        await().untilAsserted(() -> verify(reactionRepository).save(argThat(reaction ->
                reaction.reactionId().equals("reactionWithoutVersion") && reaction.reactionModelVersion() == 1)));
    }

    @Test
    void onReactionIdentifiedEvent() {
        // given: an identified reaction
        TestReaction testReaction = new TestReaction(
                TestObservation.ofEvent("MyEvent"), singletonList(TestObservation.ofCommand("MyCommand")), "reaction1");
        ReactionIdentifiedEvent event = ReactionIdentifiedV2EventBuilder.buildEvent("test", "test", testReaction);

        // when: the identified reaction is notified to the reaction observer service
        sendSync("reaction-identified", event);

        // then: the identified reaction is stored in the repository
        Reaction expectedReaction = testReaction.createExpectedReaction(event);
        await().untilAsserted(() -> verify(reactionRepository).save(expectedReaction));
    }

    @Test
    void onReactionIdentifiedEvent_triggerOnly() {
        // given: an identified reaction
        TestReaction testReaction = new TestReaction(
                TestObservation.ofEvent("MyEvent"), null, "reaction1");
        ReactionIdentifiedEvent event = ReactionIdentifiedV2EventBuilder.buildEvent("test", "test", testReaction);

        // when: the identified reaction is notified to the reaction observer service
        sendSync("reaction-identified", event);

        // then: the identified reaction is stored in the repository
        Reaction expectedReaction = testReaction.createExpectedReaction(event);
        await().untilAsserted(() -> verify(reactionRepository).save(expectedReaction));
    }

    @Test
    void onReactionIdentifiedEvent_actionOnly() {
        // given: an identified reaction
        TestReaction testReaction = new TestReaction(
                null, singletonList(TestObservation.ofCommand("MyCommand")), "reaction1");
        ReactionIdentifiedEvent event = ReactionIdentifiedV2EventBuilder.buildEvent("test", "test", testReaction);

        // when: the identified reaction is notified to the reaction observer service
        sendSync("reaction-identified", event);

        // then: the identified reaction is stored in the repository
        Reaction expectedReaction = testReaction.createExpectedReaction(event);
        await().untilAsserted(() -> verify(reactionRepository).save(expectedReaction));
    }

    @BeforeAll
    static void setUp() {
        Awaitility.setDefaultTimeout(Duration.ofSeconds(30));
    }

    @TestConfiguration
    static class TestConfig {
    }

    @SpringBootApplication
    static class TestApp {
    }
}
