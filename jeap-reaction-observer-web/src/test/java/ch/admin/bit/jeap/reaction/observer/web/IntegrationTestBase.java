package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.messaging.annotations.JeapMessageConsumerContract;
import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionIdentifiedV2EventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionsObservedEventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;
import org.awaitility.Awaitility;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

@JeapMessageConsumerContract(value = ReactionIdentifiedEvent.TypeRef.class,
        appName = "test", topic = "reaction-identified")
@JeapMessageConsumerContract(value = ReactionsObservedEvent.TypeRef.class,
        appName = "test", topic = "reactions-observed")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
abstract class IntegrationTestBase extends KafkaIntegrationTestBase {

    @Autowired
    ReactionRepository reactionRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    MockMvc mvc;
    @Autowired
    Flyway flyway;

    @Container
    private static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16.2")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeAll
    static void setUp() {
        Awaitility.setDefaultTimeout(Duration.ofSeconds(30));
        Awaitility.setDefaultPollInterval(Duration.ofSeconds(1));
    }

    @BeforeEach
    void resetDatabase() {
        flyway.clean();
        flyway.migrate();
    }

    static ReactionsObservedEvent createReactionsObservedEvent(TestReaction testReaction) {
        Instant now = Instant.now();
        return new ReactionsObservedEventBuilder("test1", "system")
                .serviceInstanceIdentifier(UUID.randomUUID())
                .countByReactionId(Map.of(testReaction.id(), 10))
                .timeframe(now.minusSeconds(300), now)
                .idempotenceId(UUID.randomUUID().toString())
                .build();
    }

    Integer observedReactionIsPersisted(String idempotenceId) {
        return jdbcTemplate.queryForObject("select count(*) from observed_reaction where idempotence_id=?", Integer.class, idempotenceId);
    }

    void sendAndAwaitReactionPersistence(TestReaction testReaction, String system, String component) {
        // Build the event
        ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent identifiedEvent = ReactionIdentifiedV2EventBuilder.buildEvent(system, component, testReaction);

        // Send the event
        sendSync("reaction-identified", identifiedEvent);

        // Create expected domain reaction
        Reaction expectedReaction = testReaction.createExpectedReaction(identifiedEvent);

        // Await persistence
        await().until(() ->
                reactionRepository.findByComponentAndReactionId(expectedReaction.component(), expectedReaction.reactionId()).isPresent()
        );
    }

    void sendAndAwaitObservedEventForReaction(TestReaction testReaction, String system, String component, int count) {
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
}
