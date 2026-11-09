package ch.admin.bit.jeap.reaction.observer.domain.models;

/**
 * How often a reaction was observed in a timeframe and how many unidentified actions occurred during the counted
 * reactions. An unidentified action is an action the emitter detects while observing a reaction but cannot identify.
 *
 * @param reactionModelVersion                the reaction model version of the emitter
 * @param minUnidentifiedActions              minimum number of different unidentified actions per observed reaction
 * @param observationsWithUnidentifiedActions number of times the reaction was observed with at least one unidentified
 *                                            action
 * @param unidentifiedActions                 total number of unidentified actions observed by the counted reactions
 */
public record ObservedReaction(String component, String reactionId, Timeframe timeframe, int count,
                               int reactionModelVersion, int minUnidentifiedActions,
                               int observationsWithUnidentifiedActions, int unidentifiedActions) {

    /**
     * A count of an emitter that predates the reaction model version and does not detect unidentified actions.
     */
    public ObservedReaction(String component, String reactionId, Timeframe timeframe, int count) {
        this(component, reactionId, timeframe, count, 1, 0, 0, 0);
    }
}
