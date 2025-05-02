package ch.admin.bit.jeap.reaction.observer.service.test;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.reaction.observer.event.identified.*;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;

import java.util.Map;

public class ReactionIdentifiedEventBuilder extends AvroDomainEventBuilder<ReactionIdentifiedEventBuilder, ReactionIdentifiedEvent> {

    private final String serviceName;
    private final String systemName;
    private TestReaction reaction;

    public static ReactionIdentifiedEvent buildEvent(String systemName, String serviceName, TestReaction reaction) {
        ReactionIdentifiedEventBuilder builder = new ReactionIdentifiedEventBuilder(serviceName, systemName);
        builder.setReaction(reaction);
        return builder.build();
    }

    private void setReaction(TestReaction reaction) {
        if (reaction.action() == null && reaction.trigger() == null) {
            throw new IllegalArgumentException("Reaction must have at least an action or a trigger");
        }
        this.reaction = reaction;
        idempotenceId("ri_" + reaction.id());
    }

    private ReactionIdentifiedEventBuilder(String serviceName, String systemName) {
        super(ReactionIdentifiedEvent::new);
        this.serviceName = serviceName;
        this.systemName = systemName;
    }

    @Override
    public ReactionIdentifiedEvent build() {
        Object reactionPayload;
        if (reaction.action() == null) {
            Observation observation = createObservation(reaction.trigger());
            reactionPayload = new TriggerOnly(observation);
        } else if (reaction.trigger() == null) {
            Observation observation = createObservation(reaction.action());
            reactionPayload = new ActionOnly(observation);
        } else {
            reactionPayload = new ch.admin.bit.jeap.reaction.observer.event.identified.Reaction(
                    createObservation(reaction.trigger()), createObservation(reaction.action()));
        }
        setPayload(new ReactionIdentifiedPayload(reaction.id(), reactionPayload));
        return super.build();
    }

    private Observation createObservation(TestObservation observation) {
        Map<String, String> props = observation.props();
        if (props == null) {
            props = Map.of();
        }
        return new Observation(observation.type(), observation.fqn(), props);
    }

    @Override
    protected String getServiceName() {
        return serviceName;
    }

    @Override
    protected String getSystemName() {
        return systemName;
    }

    @Override
    protected ReactionIdentifiedEventBuilder self() {
        return this;
    }
}
