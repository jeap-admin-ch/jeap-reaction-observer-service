package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.Action;

import java.util.List;
import java.util.Map;

public record ObservedReactionsAggregatedStatistics(String component, String triggerType, String triggerFqn, List<Action> actions, long count, double median, Double percentage, Map<String, String> triggerProperties) {
}
