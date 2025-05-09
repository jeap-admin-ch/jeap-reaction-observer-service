package ch.admin.bit.jeap.reaction.observer.domain;

public record ObservedReactionsAggregatedStatistics(String component, String triggerType, String triggerFqn, String actionType, String actionFqn, int count, float median, float percentage) {
}
