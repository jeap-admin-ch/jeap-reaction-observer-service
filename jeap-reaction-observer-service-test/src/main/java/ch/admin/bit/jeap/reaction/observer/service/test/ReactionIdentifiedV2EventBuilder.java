package ch.admin.bit.jeap.reaction.observer.service.test;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.*;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;

import java.util.List;
import java.util.Map;

public class ReactionIdentifiedV2EventBuilder extends AvroDomainEventBuilder<ReactionIdentifiedV2EventBuilder, ReactionIdentifiedEvent> {

    private final String serviceName;
    private final String systemName;
    private TestReaction reaction;

    public static ReactionIdentifiedEvent buildEvent(String systemName, String serviceName, TestReaction reaction) {
        ReactionIdentifiedV2EventBuilder builder = new ReactionIdentifiedV2EventBuilder(serviceName, systemName);
        builder.setReaction(reaction);
        return builder.build();
    }

    private void setReaction(TestReaction reaction) {
        if ((reaction.actions() == null || reaction.actions().isEmpty()) && reaction.trigger() == null) {
            throw new IllegalArgumentException("Reaction must have at least an action or a trigger");
        }
        this.reaction = reaction;
        idempotenceId("ri_" + reaction.id());
    }

    private ReactionIdentifiedV2EventBuilder(String serviceName, String systemName) {
        super(ReactionIdentifiedEvent::new);
        this.serviceName = serviceName;
        this.systemName = systemName;
    }

    @Override
    public ReactionIdentifiedEvent build() {
        Object reactionPayload;
        if (reaction.actions() == null || reaction.actions().isEmpty()) {
            Observation observation = createObservation(reaction.trigger());
            reactionPayload = new TriggerOnly(reaction.id(), observation);
        } else if (reaction.trigger() == null) {
            Observation observation = createObservation(reaction.actions().getFirst());
            reactionPayload = new ActionOnly(reaction.id(), observation);
        } else {
            reactionPayload = new Reaction(reaction.id(),
                    createObservation(reaction.trigger()), createObservations(reaction.actions()));
        }
        setPayload(new ReactionIdentifiedPayload(reactionPayload));
        return super.build();
    }

    private Observation createObservation(TestObservation observation) {
        Map<String, String> props = observation.props();
        if (props == null) {
            props = Map.of();
        }
        return new Observation(observation.id(), observation.type(), observation.fqn(), props);
    }

    private List<Observation> createObservations(List<TestObservation> observations) {
        return observations.stream()
                .map(this::createObservation)
                .toList();
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
    protected ReactionIdentifiedV2EventBuilder self() {
        return this;
    }
}
