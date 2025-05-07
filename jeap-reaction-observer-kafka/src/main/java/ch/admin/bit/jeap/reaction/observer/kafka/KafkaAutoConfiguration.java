package ch.admin.bit.jeap.reaction.observer.kafka;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ReactionObserverKafkaProperties.class)
public class KafkaAutoConfiguration {
    @Bean
    ReactionIdentifiedEventListener reactionIdentifiedEventListener(ReactionRepository repository) {
        return new ReactionIdentifiedEventListener(repository);
    }

    @Bean
    ReactionsObservedEventListener reactionsObservedEventListener(ObservedReactionRepository repository) {
        return new ReactionsObservedEventListener(repository);
    }
}
