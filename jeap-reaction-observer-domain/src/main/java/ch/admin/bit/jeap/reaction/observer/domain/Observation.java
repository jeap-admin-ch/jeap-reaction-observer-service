package ch.admin.bit.jeap.reaction.observer.domain;

import java.util.Map;

public record Observation(String type, String fqn, Map<String, String> props) {
}
