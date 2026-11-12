package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

import lombok.Builder;

/**
 * Node representing a message in the graph.
 * Used to model events or commands within the system in different variants.
 *
 * @param type the observation type as it was reported; derived from the semantic when not given
 * @param fqn  the FQN as it was reported; {@code <messageType>[/<variant>]} when not given
 */
@Builder
public record Message(
        long id,
        String type,
        String fqn,
        String messageType,
        String variant,
        SemanticType semantic
) implements Interface {

    public static final String OBSERVATION_TYPE_EVENT = "event";
    public static final String OBSERVATION_TYPE_COMMAND = "command";

    public Message(long id, String type, String fqn, String messageType, String variant, SemanticType semantic) {
        this.id = id;
        this.type = type != null || semantic == null ? type : observationTypeOf(semantic);
        this.fqn = fqn != null || messageType == null ? fqn : fqnOf(messageType, variant);
        this.messageType = messageType;
        this.variant = variant;
        this.semantic = semantic;
    }

    /** A message whose observation type and FQN are derived from its parts. */
    public Message(long id, String messageType, String variant, SemanticType semantic) {
        this(id, null, null, messageType, variant, semantic);
    }

    private static String observationTypeOf(SemanticType semantic) {
        return switch (semantic) {
            case EVENT -> OBSERVATION_TYPE_EVENT;
            case COMMAND -> OBSERVATION_TYPE_COMMAND;
        };
    }

    private static String fqnOf(String messageType, String variant) {
        return variant == null ? messageType : messageType + "/" + variant;
    }
}
