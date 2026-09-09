package ch.admin.bit.jeap.reaction.observer.web;

/**
 * How one variant of a message type is addressed in the answer of {@code /api/graphs/messages/{messageType}}:
 * the message type alone when it has no variant, and {@code messageType/variant} otherwise.
 * <p>
 * It is here rather than inline in the controller because the index of message types has to name the same keys
 * the graph resource answers with. Two places building the same string is how they come to disagree.
 */
public final class MessageGraphKey {

    private static final String SEPARATOR = "/";

    private MessageGraphKey() {
    }

    /**
     * @param messageType the message type, never null
     * @param variant     the variant, or null when the message has none
     */
    public static String of(String messageType, String variant) {
        return variant == null ? messageType : messageType + SEPARATOR + variant;
    }
}
