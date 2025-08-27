package ch.admin.bit.jeap.reaction.observer.domain.models;

public record ObservedReaction(String component, String reactionId, Timeframe timeframe, int count) {
}
