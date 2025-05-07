package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Slf4j
class ReactionRepositoryImplTest {

    @Autowired
    private ReactionRepository reactionRepository;

    @Test
    void save_noProps() {
        Observation trigger = new Observation("triggerType", "triggerFqn", Map.of());
        Observation action = new Observation("actionType", "actionFqn", Map.of());
        var identifiedReaction = new Reaction("component0", "reaction0", trigger, action, ZonedDateTime.now());

        reactionRepository.save(identifiedReaction);

        var foundReaction = reactionRepository.findByComponentAndReactionId("component0", "reaction0");
        assertThat(foundReaction)
                .isPresent();
        Reaction reaction = foundReaction.get();
        assertThat(reaction)
                .isEqualTo(identifiedReaction);
    }

    @Test
    void save_withProps() {
        Observation trigger = new Observation("triggerType", "triggerFqn", Map.of("key1", "value2"));
        Observation action = new Observation("actionType", "actionFqn", Map.of("key2", "value2"));
        var identifiedReaction = new Reaction("component1", "reaction1", trigger, action, ZonedDateTime.now());

        reactionRepository.save(identifiedReaction);

        var foundReaction = reactionRepository.findByComponentAndReactionId("component1", "reaction1");
        assertThat(foundReaction)
                .isPresent();
        Reaction reaction = foundReaction.get();
        assertThat(reaction)
                .isEqualTo(identifiedReaction);
    }

    @Test
    void save_actionOnly() {
        Observation trigger = null;
        Observation action = new Observation("actionOnlyType", "actionFqn", Map.of("key2", "value2"));
        var identifiedReaction = new Reaction("component2", "reactionActionOnly", trigger, action, ZonedDateTime.now());

        reactionRepository.save(identifiedReaction);

        var foundReaction = reactionRepository.findByComponentAndReactionId("component2", "reactionActionOnly");
        assertThat(foundReaction)
                .isPresent();
        Reaction reaction = foundReaction.get();
        assertThat(reaction)
                .isEqualTo(identifiedReaction);
    }

    @Test
    void save_triggerOnly() {
        Observation trigger = new Observation("triggerOnlyType", "triggerFqn", Map.of("key1", "value2"));
        Observation action = null;
        var identifiedReaction = new Reaction("component3", "reactionTriggerOnly", trigger, action, ZonedDateTime.now());

        reactionRepository.save(identifiedReaction);

        var foundReaction = reactionRepository.findByComponentAndReactionId("component3", "reactionTriggerOnly");
        assertThat(foundReaction)
                .isPresent();
        Reaction reaction = foundReaction.get();
        assertThat(reaction)
                .isEqualTo(identifiedReaction);
    }

    @Test
    void save_isIdempotent() {
        Observation trigger = new Observation("triggerType", "triggerFqn", Map.of());
        Observation action = new Observation("actionType", "actionFqn", Map.of());
        var identifiedReaction = new Reaction("component4", "reaction1", trigger, action, ZonedDateTime.now());

        reactionRepository.save(identifiedReaction);
        reactionRepository.save(identifiedReaction);
        reactionRepository.save(identifiedReaction);

        var foundReaction = reactionRepository.findByComponentAndReactionId("component4", "reaction1");
        assertThat(foundReaction)
                .isPresent();
        Reaction reaction = foundReaction.get();
        assertThat(reaction)
                .isEqualTo(identifiedReaction);
    }
}
