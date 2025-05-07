package ch.admin.bit.jeap.reaction.observer.kafka;

import ch.admin.bit.jeap.messaging.annotations.JeapMessageConsumerContract;
import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.reaction.observer.domain.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionIdentifiedEventBuilder;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

@JeapMessageConsumerContract(value = ReactionIdentifiedEvent.TypeRef.class,
        appName = "test", topic = "reaction-identified")
@SpringBootTest
class ReactionIdentifiedEventListenerTest extends KafkaIntegrationTestBase {

    @MockitoBean
    private ReactionRepository reactionRepository;

    @Test
    void onReactionIdentifiedEvent() {
        // given: an identified reaction
        TestReaction testReaction = new TestReaction(
                TestObservation.ofEvent("MyEvent"), TestObservation.ofCommand("MyCommand"));
        ReactionIdentifiedEvent event = ReactionIdentifiedEventBuilder.buildEvent("test", "test", testReaction);

        // when: the identified reaction is notified to the reaction observer service
        sendSync("reaction-identified", event);

        // then: the identified reaction is stored in the repository
        Reaction expectedReaction = createExpectedReaction(event, testReaction);
        await().untilAsserted(() -> verify(reactionRepository).save(expectedReaction));
    }

    @Test
    void onReactionIdentifiedEvent_triggerOnly() {
        // given: an identified reaction
        TestReaction testReaction = new TestReaction(
                TestObservation.ofEvent("MyEvent"), null);
        ReactionIdentifiedEvent event = ReactionIdentifiedEventBuilder.buildEvent("test", "test", testReaction);

        // when: the identified reaction is notified to the reaction observer service
        sendSync("reaction-identified", event);

        // then: the identified reaction is stored in the repository
        Reaction expectedReaction = createExpectedReaction(event, testReaction);
        await().untilAsserted(() -> verify(reactionRepository).save(expectedReaction));
    }

    @Test
    void onReactionIdentifiedEvent_actionOnly() {
        // given: an identified reaction
        TestReaction testReaction = new TestReaction(
                null, TestObservation.ofCommand("MyCommand"));
        ReactionIdentifiedEvent event = ReactionIdentifiedEventBuilder.buildEvent("test", "test", testReaction);

        // when: the identified reaction is notified to the reaction observer service
        sendSync("reaction-identified", event);

        // then: the identified reaction is stored in the repository
        Reaction expectedReaction = createExpectedReaction(event, testReaction);
        await().untilAsserted(() -> verify(reactionRepository).save(expectedReaction));
    }

    private static Reaction createExpectedReaction(ReactionIdentifiedEvent event, TestReaction testReaction) {
        Observation trigger = null;
        if (testReaction.trigger() != null) {
            trigger = new Observation(
                    testReaction.trigger().type(), testReaction.trigger().fqn(), testReaction.trigger().props());
        }

        Observation action = null;
        if (testReaction.action() != null) {
            action = new Observation(
                    testReaction.action().type(), testReaction.action().fqn(), testReaction.action().props());
        }

        return new Reaction(event.getPublisher().getService(), event.getPayload().getReactionId(),
                trigger, action,
                ZonedDateTime.ofInstant(event.getIdentity().getCreated(), ZoneId.systemDefault()));
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
