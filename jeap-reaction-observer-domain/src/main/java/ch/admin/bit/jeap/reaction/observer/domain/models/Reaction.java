package ch.admin.bit.jeap.reaction.observer.domain.models;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * An identified reaction.
 *
 * @param reactionModelVersion the reaction model version of the emitter that identified the reaction last
 */
public record Reaction(String system, String component, String reactionId,
                       Observation trigger, List<Observation> actions,
                       ZonedDateTime identifiedAt, int reactionModelVersion) {

    /**
     * A reaction identified by an emitter that predates the reaction model version.
     */
    public Reaction(String system, String component, String reactionId,
                    Observation trigger, List<Observation> actions,
                    ZonedDateTime identifiedAt) {
        this(system, component, reactionId, trigger, actions, identifiedAt, 1);
    }
}
