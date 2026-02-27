package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.models.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Slf4j
class ReactionRepositoryImplTest {

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private JpaReactionRepository jpaReactionRepository;

    @Autowired
    private JpaObservationPropertiesRepository jpaObservationPropertiesRepository;

    @MockitoSpyBean
    private JpaInterfaceRepository jpaInterfaceRepository;

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

    @Test
    void save_reusesExistingInterface() {
        Observation trigger1 = new Observation("t1", "sharedType", "sharedFqn", Map.of());
        Observation action1 = new Observation("a1", "sharedType", "sharedFqn", Map.of());
        Reaction reaction1 = new Reaction("system0", "componentX", "reactionX1", trigger1, List.of(action1), ZonedDateTime.now());

        Observation trigger2 = new Observation("t2", "sharedType", "sharedFqn", Map.of());
        Observation action2 = new Observation("a2", "sharedType", "sharedFqn", Map.of());
        Reaction reaction2 = new Reaction("system0", "componentX", "reactionX2", trigger2, List.of(action2), ZonedDateTime.now());

        reactionRepository.save(reaction1);
        reactionRepository.save(reaction2);

        List<InterfaceEntity> interfaces = jpaInterfaceRepository.findAll();
        assertThat(interfaces)
                .hasSize(1)
                .first()
                .satisfies(i -> {
                    assertThat(i.getType()).isEqualTo("sharedType");
                    assertThat(i.getFqn()).isEqualTo("sharedFqn");
                });
    }

    @Test
    void save_createsInterfaceEntity() {
        Observation trigger = new Observation("t1", "newType", "newFqn", Map.of());
        Observation action = new Observation("a1", "newType", "newFqn", Map.of());
        Reaction reaction = new Reaction("system0", "componentY", "reactionY", trigger, List.of(action), ZonedDateTime.now());

        reactionRepository.save(reaction);

        Optional<InterfaceEntity> interfaceEntity = jpaInterfaceRepository.findByTypeAndFqn("newType", "newFqn");
        assertThat(interfaceEntity).isPresent();
    }

    @Test
    void save_doesNotDuplicateInterface() {
        Observation trigger = new Observation("t1", "dupType", "dupFqn", Map.of());
        Observation action = new Observation("a1", "dupType", "dupFqn", Map.of());
        Reaction reaction = new Reaction("system0", "componentD", "reactionD", trigger, List.of(action), ZonedDateTime.now());

        reactionRepository.save(reaction);
        reactionRepository.save(reaction); // idempotent
        List<InterfaceEntity> interfaces = jpaInterfaceRepository.findAll();
        assertThat(interfaces).hasSize(1);
    }

    @Test
    void save_triggerWithNullFields_skipsInterfaceResolution() {
        // trigger has null type and fqn — the guard condition
        // "trigger.type() != null && trigger.fqn() != null" must prevent interface resolution
        Observation triggerWithNullType = new Observation("t1", null, null, Map.of());
        Reaction reaction = new Reaction("system0", "componentNull", "reactionNullTrigger",
                triggerWithNullType, List.of(), ZonedDateTime.now());

        reactionRepository.save(reaction);

        // no interface must have been looked up or inserted
        verify(jpaInterfaceRepository, never()).findByTypeAndFqn(anyString(), anyString());
        verify(jpaInterfaceRepository, never()).insertIfNotExists(anyString(), anyString());

        // reaction is saved but has no triggerInterface
        Optional<ReactionEntity> entity = jpaReactionRepository.findByComponentAndReactionId("componentNull", "reactionNullTrigger");
        assertThat(entity).isPresent();
        assertThat(entity.get().getTriggerInterface()).isNull();
    }

    @Test
    void save_nullObservation_skipsProps() {
        // trigger is null → saveProps(reactionId, null, true) must be a no-op
        Observation action = new Observation("a1", "actionType", "actionFqn", Map.of());
        Reaction reaction = new Reaction("system0", "componentNullObs", "reactionNullObs",
                null, List.of(action), ZonedDateTime.now());

        // must not throw
        reactionRepository.save(reaction);

        Optional<ReactionEntity> entity = jpaReactionRepository.findByComponentAndReactionId("componentNullObs", "reactionNullObs");
        assertThat(entity).isPresent();
        assertThat(entity.get().getTriggerInterface()).isNull();
    }

    @Test
    void save_emptyProps_skipsProps() {
        // both trigger and action have empty props → saveProps must be a no-op (no ObservationProperty rows)
        Observation trigger = new Observation("t1", "triggerType", "triggerFqn", Map.of());
        Observation action = new Observation("a1", "actionType", "actionFqn", Map.of());
        Reaction reaction = new Reaction("system0", "componentEmptyProps", "reactionEmptyProps",
                trigger, List.of(action), ZonedDateTime.now());

        reactionRepository.save(reaction);

        Optional<ReactionEntity> entity = jpaReactionRepository.findByComponentAndReactionId("componentEmptyProps", "reactionEmptyProps");
        assertThat(entity).isPresent();
        // no props must have been stored
        assertThat(entity.get().getId()).isNotNull();
        long propCount = jpaObservationPropertiesRepository
                .findByReactionTriggerFk(entity.get().getId()).count();
        assertThat(propCount).isZero();
    }

    @Test
    void resolveInterface_throwsIllegalStateException_whenInterfaceNotFoundAfterInsert() {
        // Simulate insertIfNotExists succeeding silently but findByTypeAndFqn returning empty
        // (e.g. a bug or race condition where the row disappeared after insert)
        doNothing().when(jpaInterfaceRepository).insertIfNotExists(anyString(), anyString());

        Observation trigger = new Observation("t1", "ghostType", "ghostFqn", Map.of());
        Reaction reaction = new Reaction("system0", "componentGhost", "reactionGhost",
                trigger, List.of(), ZonedDateTime.now());

        assertThatThrownBy(() -> reactionRepository.save(reaction))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Interface not found after insert")
                .hasMessageContaining("ghostType")
                .hasMessageContaining("ghostFqn");
    }
}
