package ch.admin.bit.jeap.reaction.observer.kafka;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ActionOnly;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.Observation;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.TriggerOnly;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
class ReactionIdentifiedEventListener {

    private final ReactionRepository reactionRepository;

    ReactionIdentifiedEventListener(ReactionRepository reactionRepository) {
        this.reactionRepository = reactionRepository;
    }

    @KafkaListener(topics = "${jeap.reaction.observer.service.kafka.reaction-identified-topic}")
    public void onReactionIdentifiedEvent(AvroMessage event, Acknowledgment ack) {
        if (event instanceof ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent) {
            log.trace("Received a v1 ReactionIdentifiedEvent, which is not supported by this listener anymore. Ignoring event: {}", event);
            ack.acknowledge();
            return;
        }
        Reaction reaction = createReaction((ReactionIdentifiedEvent) event);
        log.debug("Identified reaction: {}", reaction);
        reactionRepository.save(reaction);
        ack.acknowledge();
    }

    private static Reaction createReaction(ReactionIdentifiedEvent event) {
        var reactionPayload = event.getPayload().getReaction();
        List<Observation> actions = null;
        Observation trigger = null;
        String reactionId;
        switch (reactionPayload) {
            case ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction reaction -> {
                reactionId = reaction.getReactionId();
                actions = reaction.getActions();
                trigger = reaction.getTrigger();
            }
            case TriggerOnly triggerOnly -> {
                reactionId = triggerOnly.getReactionId();
                trigger = triggerOnly.getTrigger();
            }
            case ActionOnly actionOnly -> {
                reactionId = actionOnly.getReactionId();
                actions = List.of(actionOnly.getAction());
            }
            default ->
                    throw new IllegalArgumentException("Unknown reaction payload type: " + reactionPayload.getClass());
        }

        return createReaction(event, reactionId, trigger, actions);
    }

    private static Reaction createReaction(ReactionIdentifiedEvent event, String reactionId, Observation trigger, List<Observation> actions) {
        return new Reaction(
                event.getPublisher().getSystem(),
                event.getPublisher().getService(),
                reactionId,
                toDomainObservation(trigger),
                toDomainObservationList(actions),
                ZonedDateTime.ofInstant(event.getIdentity().getCreated(), ZoneId.systemDefault()));
    }

    private static ch.admin.bit.jeap.reaction.observer.domain.models.Observation toDomainObservation(Observation observation) {
        if (observation == null) {
            return null;
        }
        return new ch.admin.bit.jeap.reaction.observer.domain.models.Observation(observation.getId(), observation.getType(), observation.getFqn(), observation.getProps());
    }

    private static List<ch.admin.bit.jeap.reaction.observer.domain.models.Observation> toDomainObservationList(List<Observation> observations) {
        if (observations == null || observations.isEmpty()) {
            return List.of();
        }
        return observations.stream()
                .map(ReactionIdentifiedEventListener::toDomainObservation)
                .toList();
    }
}
