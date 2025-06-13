package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
class ReactionRepositoryImpl implements ReactionRepository {

    private final JpaReactionRepository jpaReactionRepository;
    private final JpaObservationPropertiesRepository jpaObservationPropertiesRepository;

    @Transactional
    @Override
    public void save(Reaction reaction) {
        if (jpaReactionRepository.existsByComponentAndReactionId(reaction.component(), reaction.reactionId())) {
            return;
        }

        try {
            Observation trigger = reaction.trigger();

            var builder = ReactionEntity.builder()
                    .component(reaction.component())
                    .reactionId(reaction.reactionId())
                    .identifiedAt(reaction.identifiedAt());
            if (trigger != null) {
                builder.triggerId(reaction.trigger().id());
                builder.triggerType(trigger.type());
                builder.triggerFqn(trigger.fqn());
            }

            ReactionEntity reactionEntity = builder.build();
            for (Observation action : reaction.actions()) {
                ActionEntity actionEntity = ActionEntity.builder()
                        .reaction(reactionEntity)
                        .actionId(action.id())
                        .actionType(action.type())
                        .actionFqn(action.fqn())
                        .build();
                reactionEntity.addAction(actionEntity);
            }

            Long reactionId = jpaReactionRepository.save(reactionEntity).getId();
            saveProps(reactionId, trigger, true);
            for (ActionEntity actionEntity : reactionEntity.getActions()) {
                reaction.actions()
                        .stream()
                        .filter(action -> action.id().equals(actionEntity.getActionId())).findFirst()
                        .ifPresent(action -> saveProps(actionEntity.getId(), action, false));
            }
        } catch (DuplicateKeyException ex) {
            log.debug("Identified reaction already exists, ignoring", ex);
        }
    }

    private void saveProps(Long reactionId, Observation observation, boolean isTrigger) {
        if (observation == null || observation.props() == null || observation.props().isEmpty()) {
            return;
        }

        observation.props().forEach((key, value) -> {
            ObservationProperty.ObservationPropertyBuilder builder = ObservationProperty.builder()
                    .key(key)
                    .value(value);
            if (isTrigger) {
                builder.reactionTriggerFk(reactionId);
            } else {
                builder.actionFk(reactionId);
            }
            jpaObservationPropertiesRepository.save(builder.build());
        });
    }

    @Override
    @Transactional
    public Optional<Reaction> findByComponentAndReactionId(String component, String reactionId) {
        return jpaReactionRepository.findByComponentAndReactionId(component, reactionId)
                .map(entity -> {
                    List<Observation> actions = entity.getActions().stream().map(
                            actionEntity -> new Observation(
                                    actionEntity.getActionId(),
                                    actionEntity.getActionType(),
                                    actionEntity.getActionFqn(),
                                    loadProps(actionEntity.getId(), false)
                            )).collect(Collectors.toList());
                    return new Reaction(
                            entity.getComponent(),
                            entity.getReactionId(),
                            observation(entity.getTriggerId(), entity.getTriggerType(), entity.getTriggerFqn(), loadProps(entity.getId(), true)),
                            actions,
                            entity.getIdentifiedAt());
                });
    }

    private Map<String, String> loadProps(Long reactionId, boolean isTrigger) {
        if (isTrigger) {
            return jpaObservationPropertiesRepository.findByReactionTriggerFk(reactionId)
                    .collect(Collectors.toMap(ObservationProperty::getKey, ObservationProperty::getValue));
        } else {
            return jpaObservationPropertiesRepository.findByActionFk(reactionId)
                    .collect(Collectors.toMap(ObservationProperty::getKey, ObservationProperty::getValue));
        }
    }

    private Observation observation(String id, String type, String fqn, Map<String, String> props) {
        if (type == null) {
            return null;
        }
        return new Observation(id, type, fqn, props);
    }

    private String getTriggerId(String reactionId) {
        String triggerIdFragment = reactionId.split("#")[0];
        return triggerIdFragment.isBlank() ? null : triggerIdFragment;
    }

    private String getActionId(String reactionId) {
        String[] parts = reactionId.split("#");
        if (parts.length > 1) {
            return parts[1];
        }
        return null;
    }
}
