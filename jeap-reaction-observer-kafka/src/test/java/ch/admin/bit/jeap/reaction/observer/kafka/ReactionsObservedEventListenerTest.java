package ch.admin.bit.jeap.reaction.observer.kafka;

import ch.admin.bit.jeap.messaging.annotations.JeapMessageConsumerContract;
import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionGraphRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.ObservedReaction;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.Timeframe;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionsObservedEventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestUnidentifiedActions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

@JeapMessageConsumerContract(value = ReactionsObservedEvent.TypeRef.class,
        appName = "test", topic = "reactions-observed")
@SpringBootTest
class ReactionsObservedEventListenerTest extends KafkaIntegrationTestBase {

    @MockitoBean
    private ObservedReactionRepository observedReactionRepository;

    @MockitoBean
    private ReactionRepository reactionRepository;

    @MockitoBean
    private ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;

    @MockitoBean
    private ReactionGraphRepository reactionGraphRepository;


    @Test
    void onReactionsObservedEvent_takesTheReactionModelVersionAndTheUnidentifiedActionsFromTheEvent() {
        // given: observed reactions with a reaction model version and unidentified actions
        Instant now = Instant.now();
        ReactionsObservedEvent event = new ReactionsObservedEventBuilder("test", "system")
                .serviceInstanceIdentifier(UUID.randomUUID())
                .countByReactionId(Map.of("r1", 10))
                .reactionModelVersion(2)
                .unidentifiedActionsByReactionId(Map.of("r1", new TestUnidentifiedActions(1, 3, 7)))
                .timeframe(now.minusSeconds(300), now)
                .build();

        // when: the observed reactions are notified to the reaction observer service
        sendSync("reactions-observed", event);

        // then: the values of the event are stored in the repository
        var timeframe = event.getPayload().getTimeframe();
        ObservedReaction expected = new ObservedReaction("test", "r1",
                Timeframe.ofInstantsInDefaultTimezone(timeframe.getStart(), timeframe.getEnd()), 10, 2, 1, 3, 7);
        await().untilAsserted(() -> verify(observedReactionRepository).saveAll(event.getIdentity().getIdempotenceId(), List.of(expected)));
    }

    @Test
    void onReactionsObservedEvent() {
        // given: observed reactions
        Instant now = Instant.now();
        ReactionsObservedEvent event = new ReactionsObservedEventBuilder("test", "system")
                .serviceInstanceIdentifier(UUID.randomUUID())
                .countByReactionId(Map.of("r1", 10, "r2", 20))
                .timeframe(now.minusSeconds(300), now)
                .build();

        // when: the observed reactions are notified to the reaction observer service
        sendSync("reactions-observed", event);

        // then: the observed reactions are stored in the repository
        List<ObservedReaction> observedReactions = toExpectation(event);
        await().untilAsserted(() -> verify(observedReactionRepository).saveAll(event.getIdentity().getIdempotenceId(), observedReactions));
    }

    private static List<ObservedReaction> toExpectation(ReactionsObservedEvent event) {
        return event.getPayload().getObservations().stream()
                .map(observation -> new ObservedReaction(
                        "test",
                        observation.getReactionId(),
                        Timeframe.ofInstantsInDefaultTimezone(event.getPayload().getTimeframe().getStart(), event.getPayload().getTimeframe().getEnd()),
                        observation.getCount()))
                .toList();
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
