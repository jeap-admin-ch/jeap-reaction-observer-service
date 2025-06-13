package ch.admin.bit.jeap.reaction.observer.domain;

import java.util.List;
import java.util.Map;

public record ObservedReactionsAggregatedStatisticsV2(String component, String triggerType, String triggerFqn, List<Action> actions, long count, double median, Double percentage, Map<String, String> triggerProperties) {
}
