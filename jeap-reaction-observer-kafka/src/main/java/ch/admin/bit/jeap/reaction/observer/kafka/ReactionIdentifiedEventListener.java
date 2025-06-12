package ch.admin.bit.jeap.reaction.observer.kafka;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.reaction.observer.domain.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import ch.admin.bit.jeap.reaction.observer.event.identified.ActionOnly;
import ch.admin.bit.jeap.reaction.observer.event.identified.Observation;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.identified.TriggerOnly;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
class ReactionIdentifiedEventListener {

    private final ReactionRepository reactionRepository;

    ReactionIdentifiedEventListener(ReactionRepository reactionRepository) {
        this.reactionRepository = reactionRepository;
    }

    @KafkaListener(topics = "${jeap.reaction.observer.service.kafka.reaction-identified-topic}")
    public void onReactionIdentifiedEvent(AvroMessage event, Acknowledgment ack) {
        if (event instanceof ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent) {
            log.trace("Received a v2 ReactionIdentifiedEvent, which is not supported by this listener. Ignoring event: {}", event);
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
        Observation action = null;
        Observation trigger = null;
        switch (reactionPayload) {
            case ch.admin.bit.jeap.reaction.observer.event.identified.Reaction reaction -> {
                action = reaction.getAction();
                trigger = reaction.getTrigger();
            }
            case TriggerOnly triggerOnly -> trigger = triggerOnly.getTrigger();
            case ActionOnly actionOnly -> action = actionOnly.getAction();
            default ->
                    throw new IllegalArgumentException("Unknown reaction payload type: " + reactionPayload.getClass());
        }

        return createReaction(event, trigger, action);
    }

    private static Reaction createReaction(ReactionIdentifiedEvent event, Observation trigger, Observation action) {
        return new Reaction(
                event.getPublisher().getService(),
                event.getPayload().getReactionId(),
                toDomainObservation(trigger),
                toDomainObservation(action),
                ZonedDateTime.ofInstant(event.getIdentity().getCreated(), ZoneId.systemDefault()));
    }

    private static ch.admin.bit.jeap.reaction.observer.domain.Observation toDomainObservation(Observation trigger) {
        if (trigger == null) {
            return null;
        }
        return new ch.admin.bit.jeap.reaction.observer.domain.Observation(trigger.getType(), trigger.getFqn(), trigger.getProps());
    }
}
