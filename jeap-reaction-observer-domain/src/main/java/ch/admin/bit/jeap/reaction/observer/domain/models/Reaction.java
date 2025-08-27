package ch.admin.bit.jeap.reaction.observer.domain.models;

import java.time.ZonedDateTime;
import java.util.List;

public record Reaction(String system, String component, String reactionId,
                       Observation trigger, List<Observation> actions,
                       ZonedDateTime identifiedAt) {

}
