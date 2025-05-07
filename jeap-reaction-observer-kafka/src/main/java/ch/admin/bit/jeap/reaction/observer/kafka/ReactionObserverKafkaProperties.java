package ch.admin.bit.jeap.reaction.observer.kafka;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "jeap.reaction.observer.service.kafka")
@Data
@Validated
@Slf4j
public class ReactionObserverKafkaProperties {

    @NotEmpty
    String reactionIdentifiedTopic;

    @NotEmpty
    String reactionsObservedTopic;

    @PostConstruct
    void logProperties() {
        log.info("Reaction observer kafka properties: {}", this);
    }
}
