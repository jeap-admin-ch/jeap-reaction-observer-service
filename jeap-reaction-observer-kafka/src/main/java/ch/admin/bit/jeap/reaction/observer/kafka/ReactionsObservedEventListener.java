package ch.admin.bit.jeap.reaction.observer.kafka;

import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import org.springframework.kafka.annotation.KafkaListener;

class ReactionsObservedEventListener {

    @KafkaListener(topics = "${jeap.reaction.observer.service.kafka.reactions-observed-topic}")
    public void onReactionsObservedEvent(ReactionsObservedEvent event) {
        // TODO: Handle the ReactionsObservedEvent
    }
}
