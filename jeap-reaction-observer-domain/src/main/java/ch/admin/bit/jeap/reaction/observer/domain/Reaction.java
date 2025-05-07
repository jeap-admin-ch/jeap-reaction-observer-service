package ch.admin.bit.jeap.reaction.observer.domain;

import java.time.ZonedDateTime;

public record Reaction(String component, String reactionId,
                       Observation trigger, Observation action,
                       ZonedDateTime identifiedAt) {

}
