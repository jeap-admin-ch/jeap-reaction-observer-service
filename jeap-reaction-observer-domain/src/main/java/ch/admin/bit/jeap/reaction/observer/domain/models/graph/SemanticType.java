package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

import java.util.Locale;
import java.util.Optional;

public enum SemanticType {
    EVENT,
    COMMAND;

    /**
     * The semantic of a message observation type, ignoring case, or empty when the type is not a message type.
     * <p>
     * Does not throw: an observation type the observer does not know (an HTTP request, or a type a newer library
     * reports) is an interface of another kind, not an error - see {@code InterfaceNodeFactory}.
     */
    public static Optional<SemanticType> fromValue(String observationType) {
        if (observationType == null) {
            return Optional.empty();
        }
        return switch (observationType.toUpperCase(Locale.ROOT)) {
            case "EVENT" -> Optional.of(EVENT);
            case "COMMAND" -> Optional.of(COMMAND);
            default -> Optional.empty();
        };
    }
}
