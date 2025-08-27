package ch.admin.bit.jeap.reaction.observer.domain.models;

import java.util.Map;

public record Observation(String id, String type, String fqn, Map<String, String> props) {
}
