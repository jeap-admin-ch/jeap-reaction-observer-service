package ch.admin.bit.jeap.reaction.observer.service.test.model;

import ch.admin.bit.jeap.reaction.observer.domain.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public record TestReaction(TestObservation trigger, TestObservation action, String id) {

    public TestReaction(TestObservation trigger, TestObservation action) {
        this(trigger, action, createId(trigger, action));
    }

    private static String createId(TestObservation trigger, TestObservation action) {
        if (trigger == null) {
            return "#" + action.id();
        } else if (action == null) {
            return trigger.id();
        } else {
            return trigger.id() + "#" + action.id();
        }
    }

    public Reaction createExpectedReaction(ReactionIdentifiedEvent event) {
        Observation trigger = null;
        if (trigger() != null) {
            trigger = new Observation(
                    trigger().type(), trigger().fqn(), trigger().props());
        }

        Observation action = null;
        if (action() != null) {
            action = new Observation(
                    action().type(), action().fqn(), action().props());
        }

        return new Reaction(event.getPublisher().getService(), event.getPayload().getReactionId(),
                trigger, action,
                ZonedDateTime.ofInstant(event.getIdentity().getCreated(), ZoneId.systemDefault()));
    }
}
