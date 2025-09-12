package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

public enum SemanticType {
    EVENT,
    COMMAND;

    public static SemanticType fromValue(String triggerType) {
        return switch (triggerType.toUpperCase()) {
            case "EVENT" -> EVENT;
            case "COMMAND" -> COMMAND;
            default -> throw new IllegalArgumentException("Unknown trigger type: " + triggerType);
        };
    }
}
