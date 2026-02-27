package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final JpaInterfaceRepository jpaInterfaceRepository;

    @Transactional
    @Override
    public void save(Reaction reaction) {
        if (jpaReactionRepository.existsByComponentAndReactionId(reaction.component(), reaction.reactionId())) {
            return;
        }

        try {
            Observation trigger = reaction.trigger();

            var builder = ReactionEntity.builder()
                    .system(reaction.system())
                    .component(reaction.component())
                    .reactionId(reaction.reactionId())
                    .identifiedAt(reaction.identifiedAt());

            if (trigger != null && trigger.id() != null && trigger.type() != null && trigger.fqn() != null) {
                builder.triggerId(trigger.id());
                builder.triggerInterface(resolveInterface(trigger.type(), trigger.fqn()));
            }

            ReactionEntity reactionEntity = builder.build();

            for (Observation action : reaction.actions()) {
                ActionEntity actionEntity = ActionEntity.builder()
                        .reaction(reactionEntity)
                        .actionId(action.id())
                        .actionInterface(resolveInterface(action.type(), action.fqn()))
                        .build();
                reactionEntity.addAction(actionEntity);
            }

            Long reactionId = jpaReactionRepository.save(reactionEntity).getId();
            saveProps(reactionId, trigger, true);

            for (ActionEntity actionEntity : reactionEntity.getActions()) {
                reaction.actions().stream()
                        .filter(action -> action.id().equals(actionEntity.getActionId()))
                        .findFirst()
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
                                    actionEntity.getActionInterface().getType(),
                                    actionEntity.getActionInterface().getFqn(),
                                    loadProps(actionEntity.getId(), false)
                            )).collect(Collectors.toList());

                    Observation trigger = null;
                    if (entity.getTriggerInterface() != null) {
                        trigger = new Observation(
                                entity.getTriggerId(),
                                entity.getTriggerInterface().getType(),
                                entity.getTriggerInterface().getFqn(),
                                loadProps(entity.getId(), true)
                        );
                    }

                    return new Reaction(
                            entity.getSystem(),
                            entity.getComponent(),
                            entity.getReactionId(),
                            trigger,
                            actions,
                            entity.getIdentifiedAt()
                    );
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

    private InterfaceEntity resolveInterface(String type, String fqn) {
        return jpaInterfaceRepository.findByTypeAndFqn(type, fqn)
        .orElseGet(() -> {
            try {
                return jpaInterfaceRepository.save(new InterfaceEntity(type, fqn));
            } catch (DataIntegrityViolationException | ConstraintViolationException e) {
                // Another tx inserted the same (type,fqn) in parallel — fetch and return it
                return jpaInterfaceRepository.findByTypeAndFqn(type, fqn)
                        .orElseThrow(() -> e);
            }
        });
    }
}
