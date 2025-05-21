package ch.admin.bit.jeap.reaction.observer.domain;

import java.util.Map;

public record ObservedReactionsAggregatedStatistics(String component, String triggerType, String triggerFqn, String actionType, String actionFqn, long count, double median, Double percentage, Map<String, String> triggerProperties, Map<String, String> actionProperties) {
}
