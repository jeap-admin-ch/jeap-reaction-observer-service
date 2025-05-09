package ch.admin.bit.jeap.reaction.observer.domain;

import java.time.LocalDate;

public record ObservedReactionsAggregated(String component, String reactionId, String triggerType, String triggerFqn, String actionType, String actionFqn, LocalDate date, int count) {
}
