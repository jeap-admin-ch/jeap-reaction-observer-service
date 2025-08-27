package ch.admin.bit.jeap.reaction.observer.service.test.model;

import ch.admin.bit.jeap.reaction.observer.domain.models.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public record TestReaction(TestObservation trigger, List<TestObservation> actions, String id) {

    public Reaction createExpectedReaction(ReactionIdentifiedEvent event) {
        Observation trigger = null;
        if (trigger() != null) {
            trigger = new Observation(
                    trigger().id(), trigger().type(), trigger().fqn(), trigger().props());
        }

        List<Observation> actions = null;
        if (actions() != null) {
            actions = actions().stream()
                    .map(a -> new Observation(a.id(), a.type(), a.fqn(), a.props()))
                    .toList();
        } else {
            actions = List.of();
        }

        return new Reaction(event.getPublisher().getSystem(), event.getPublisher().getService(), id(),
                trigger, actions,
                ZonedDateTime.ofInstant(event.getIdentity().getCreated(), ZoneId.systemDefault()));
    }
}
