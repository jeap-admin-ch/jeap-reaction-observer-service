package ch.admin.bit.jeap.reaction.observer.domain;

import java.util.Map;

public record Action(String actionType, String actionFqn, Map<String, String> actionProperties) {
}
