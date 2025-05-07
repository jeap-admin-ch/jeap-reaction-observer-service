package ch.admin.bit.jeap.reaction.observer.domain;

public record ObservedReaction(String component, String reactionId, Timeframe timeframe, int count) {
}
