package ch.admin.bit.jeap.reaction.observer.kafka;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReaction;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.Timeframe;
import ch.admin.bit.jeap.reaction.observer.event.observed.Observation;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.List;

@Slf4j

class ReactionsObservedEventListener {

    private final ObservedReactionRepository observedReactionRepository;

    ReactionsObservedEventListener(ObservedReactionRepository observedReactionRepository) {
        this.observedReactionRepository = observedReactionRepository;
    }

    @KafkaListener(topics = "${jeap.reaction.observer.service.kafka.reactions-observed-topic}")
    public void onReactionsObservedEvent(ReactionsObservedEvent event) {
        List<ObservedReaction> observedReactions = toObservedReactions(event);
        log.debug("Observed reactions: {}", observedReactions);
        observedReactionRepository.saveAll(event.getIdentity().getIdempotenceId(), observedReactions);
    }

    private List<ObservedReaction> toObservedReactions(ReactionsObservedEvent event) {
        return event.getPayload().getObservations().stream()
                .map(observation -> toDomainObject(event, observation))
                .toList();
    }

    private ObservedReaction toDomainObject(ReactionsObservedEvent event, Observation observation) {
        var timeframe = event.getPayload().getTimeframe();
        var component = event.getPublisher().getService();
        return new ObservedReaction(component, observation.getReactionId(),
                Timeframe.ofInstantsInDefaultTimezone(timeframe.getStart(), timeframe.getEnd()), observation.getCount());
    }
}
