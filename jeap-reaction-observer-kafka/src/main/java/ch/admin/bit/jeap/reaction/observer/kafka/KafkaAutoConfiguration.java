package ch.admin.bit.jeap.reaction.observer.kafka;

import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class KafkaAutoConfiguration {
    @Bean
    ReactionIdentifiedEventListener reactionIdentifiedEventListener(ReactionRepository repository) {
        return new ReactionIdentifiedEventListener(repository);
    }

    @Bean
    ReactionsObservedEventListener reactionsObservedEventListener() {
        return new ReactionsObservedEventListener();
    }
}
