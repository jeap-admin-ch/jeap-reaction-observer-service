package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.messaging.annotations.JeapMessageConsumerContract;
import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

@JeapMessageConsumerContract(value = ReactionIdentifiedEvent.TypeRef.class,
        appName = "test", topic = "reaction-identified")
@JeapMessageConsumerContract(value = ReactionsObservedEvent.TypeRef.class,
        appName = "test", topic = "reactions-observed")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SpringBootTest
abstract class IntegrationTestBase extends KafkaIntegrationTestBase {

    @Autowired
    ReactionRepository reactionRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void setUp() {
        Awaitility.setDefaultTimeout(Duration.ofSeconds(30));
        Awaitility.setDefaultPollInterval(Duration.ofSeconds(1));
    }
}
