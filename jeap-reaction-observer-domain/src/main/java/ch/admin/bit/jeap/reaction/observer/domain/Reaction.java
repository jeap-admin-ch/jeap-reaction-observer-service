package ch.admin.bit.jeap.reaction.observer.domain;

import java.time.ZonedDateTime;
import java.util.List;

public record Reaction(String component, String reactionId,
                       Observation trigger, List<Observation> actions,
                       ZonedDateTime identifiedAt) {

}
