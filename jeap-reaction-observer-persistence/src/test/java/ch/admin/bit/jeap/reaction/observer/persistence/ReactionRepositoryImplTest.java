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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Slf4j
class ReactionRepositoryImplTest {

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private JpaReactionRepository jpaReactionRepository;

    @Test
    void save_noProps() {
        Observation trigger = new Observation("t1", "triggerType", "triggerFqn", Map.of());
        Observation action = new Observation("a1", "actionType", "actionFqn", Map.of());
        var identifiedReaction = new Reaction("system0", "component0", "reaction0", trigger, List.of(action), ZonedDateTime.now());

        reactionRepository.save(identifiedReaction);

        var foundReaction = reactionRepository.findByComponentAndReactionId("component0", "reaction0");
        assertThat(foundReaction)
                .isPresent();
        Reaction reaction = foundReaction.get();
        assertThat(reaction)
                .isEqualTo(identifiedReaction);
    }

    @Test
    void save_multipleActions() {
        Observation trigger = new Observation("t1", "triggerType", "triggerFqn", Map.of());
        Observation action1 = new Observation("a1", "actionType", "actionFqn", Map.of());
        Observation action2 = new Observation("a2", "actionType", "actionFqn", Map.of());
        var identifiedReaction = new Reaction("system0","component0", "reaction1", trigger, List.of(action1, action2), ZonedDateTime.now());

        reactionRepository.save(identifiedReaction);

        var foundReaction = reactionRepository.findByComponentAndReactionId("component0", "reaction1");
        assertThat(foundReaction)
                .isPresent();
        Reaction reaction = foundReaction.get();
        assertThat(reaction)
                .isEqualTo(identifiedReaction);
        assertThat(reaction.actions())
                .hasSize(2)
                .containsExactlyInAnyOrder(action1, action2);
    }

    @Test
    void save_withProps() {
        Observation trigger = new Observation("t1", "triggerType", "triggerFqn", Map.of("key1", "value2"));
        Observation action = new Observation("a1", "actionType", "actionFqn", Map.of("key2", "value2"));
        var identifiedReaction = new Reaction("system0","component1", "reaction1", trigger, List.of(action), ZonedDateTime.now());

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
        Observation action = new Observation("a1", "actionOnlyType", "actionFqn", Map.of("key2", "value2"));
        var identifiedReaction = new Reaction("system0","component2", "reactionActionOnly", trigger, List.of(action), ZonedDateTime.now());

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
        Observation trigger = new Observation("t1", "triggerOnlyType", "triggerFqn", Map.of("key1", "value2"));
        List<Observation> actions = Collections.emptyList();
        var identifiedReaction = new Reaction("system0","component3", "reactionTriggerOnly", trigger, actions, ZonedDateTime.now());

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
        Observation trigger = new Observation("t1", "triggerType", "triggerFqn", Map.of());
        Observation action = new Observation("a1", "actionType", "actionFqn", Map.of());
        var identifiedReaction = new Reaction("system0","component4", "reaction1", trigger, List.of(action), ZonedDateTime.now());

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

    @Test
    void save_triggerId_isSaved() {
        Observation trigger = new Observation("t1", "triggerType", "triggerFqn", Map.of());
        var identifiedReaction = new Reaction("system0","component5", "reaction1", trigger, List.of(), ZonedDateTime.now());

        reactionRepository.save(identifiedReaction);

        Optional<ReactionEntity> entity = jpaReactionRepository.findByComponentAndReactionId("component5", "reaction1");
        assertThat(entity).isPresent();
        assertThat(entity.get().getTriggerId()).isEqualTo("t1");
    }

    @Test
    void save_actionId_isSaved() {
        Observation action = new Observation("a1", "actionType", "actionFqn", Map.of());
        var identifiedReaction = new Reaction("system0","component0", "#action0", null, List.of(action), ZonedDateTime.now());

        reactionRepository.save(identifiedReaction);

        Optional<ReactionEntity> entity = jpaReactionRepository.findByComponentAndReactionId("component0", "#action0");
        assertThat(entity).isPresent();
        assertThat(entity.get().getActions().getFirst().getActionId()).isEqualTo("a1");
    }

    @Test
    void save_triggerAndActionIds_areSaved() {
        Observation trigger = new Observation("t1", "triggerType", "triggerFqn", Map.of());
        Observation action = new Observation("a1", "actionType", "actionFqn", Map.of());
        var identifiedReaction = new Reaction("system0","component0", "reaction3", trigger, List.of(action), ZonedDateTime.now());

        reactionRepository.save(identifiedReaction);

        Optional<ReactionEntity> entity = jpaReactionRepository.findByComponentAndReactionId("component0", "reaction3");
        assertThat(entity).isPresent();
        assertThat(entity.get().getActions().getFirst().getActionId()).isEqualTo("a1");
        assertThat(entity.get().getTriggerId()).isEqualTo("t1");
    }

}
