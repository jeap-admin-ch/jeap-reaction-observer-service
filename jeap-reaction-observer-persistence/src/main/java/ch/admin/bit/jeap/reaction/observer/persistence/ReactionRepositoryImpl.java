package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

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
            Observation action = reaction.action();
            var builder = ReactionEntity.builder()
                    .component(reaction.component())
                    .reactionId(reaction.reactionId())
                    .identifiedAt(reaction.identifiedAt());
            if (trigger != null) {
                builder.triggerType(trigger.type());
                builder.triggerFqn(trigger.fqn());
            }
            if (action != null) {
                builder.actionType(action.type());
                builder.actionFqn(action.fqn());
            }
            Long reactionId = jpaReactionRepository.save(builder.build()).getId();

            saveProps(reactionId, trigger, true);
            saveProps(reactionId, action, false);

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
                builder.reactionActionFk(reactionId);
            }
            jpaObservationPropertiesRepository.save(builder.build());
        });
    }

    @Override
    @Transactional
    public Optional<Reaction> findByComponentAndReactionId(String component, String reactionId) {
        return jpaReactionRepository.findByComponentAndReactionId(component, reactionId)
                .map(entity -> new Reaction(
                        entity.getComponent(),
                        entity.getReactionId(),
                        observation(entity.getTriggerType(), entity.getTriggerFqn(), loadProps(entity.getId(), true)),
                        observation(entity.getActionType(), entity.getActionFqn(), loadProps(entity.getId(), false)),
                        entity.getIdentifiedAt()));
    }

    private Map<String, String> loadProps(Long reactionId, boolean isTrigger) {
        if (isTrigger) {
            return jpaObservationPropertiesRepository.findByReactionTriggerFk(reactionId)
                    .collect(Collectors.toMap(ObservationProperty::getKey, ObservationProperty::getValue));
        } else {
            return jpaObservationPropertiesRepository.findByReactionActionFk(reactionId)
                    .collect(Collectors.toMap(ObservationProperty::getKey, ObservationProperty::getValue));
        }
    }

    private Observation observation(String type, String fqn, Map<String, String> props) {
        if (type == null) {
            return null;
        }
        return new Observation(type, fqn, props);
    }
}
