package ch.admin.bit.jeap.reaction.observer.service.test.model;

/**
 * The unidentified actions reported with the count of a reaction in a {@code reactions-observed-event}.
 *
 * @param minUnidentifiedActions              minimum number of different unidentified actions per observed reaction
 * @param observationsWithUnidentifiedActions number of times the reaction was observed with at least one unidentified
 *                                            action
 * @param unidentifiedActions                 total number of unidentified actions observed by the counted reactions
 */
public record TestUnidentifiedActions(int minUnidentifiedActions, int observationsWithUnidentifiedActions,
                                      int unidentifiedActions) {

    public static final TestUnidentifiedActions NONE = new TestUnidentifiedActions(0, 0, 0);
}
