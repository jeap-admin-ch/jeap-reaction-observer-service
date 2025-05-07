package ch.admin.bit.jeap.reaction.observer.service.test;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.reaction.observer.event.observed.Observation;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedPayload;
import ch.admin.bit.jeap.reaction.observer.event.observed.Timeframe;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReactionsObservedEventBuilder extends AvroDomainEventBuilder<ReactionsObservedEventBuilder, ReactionsObservedEvent> {

    private final String serviceName;
    private final String systemName;
    private UUID serviceInstanceIdentifier;
    private Timeframe timeframe;
    private List<Observation> observations;

    public ReactionsObservedEventBuilder(String serviceName, String systemName) {
        super(ReactionsObservedEvent::new);
        this.serviceName = serviceName;
        this.systemName = systemName;
    }

    public ReactionsObservedEventBuilder serviceInstanceIdentifier(UUID serviceInstanceIdentifier) {
        this.serviceInstanceIdentifier = serviceInstanceIdentifier;
        return this;
    }

    public ReactionsObservedEventBuilder timeframe(Instant from, Instant to) {
        this.timeframe = new Timeframe(from, to);
        return this;
    }

    public ReactionsObservedEventBuilder countByReactionId(Map<String, Integer> countByReactionId) {
        this.observations = toObservations(countByReactionId);
        return this;
    }

    private List<Observation> toObservations(Map<String, Integer> countByReactionId) {
        return countByReactionId.entrySet().stream()
                .map(entry -> new Observation(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public ReactionsObservedEvent build() {
        setPayload(new ReactionsObservedPayload(timeframe, observations));
        idempotenceId(createIdempotenceId());
        return super.build();
    }

    private String createIdempotenceId() {
        return serviceName + "-" + serviceInstanceIdentifier + "-" + timeframe.getStart() + "-" + timeframe.getEnd();
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
    protected ReactionsObservedEventBuilder self() {
        return this;
    }
}
