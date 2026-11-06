package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.GenericInterface;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.HttpOperation;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Interface;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.SemanticType;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates the graph node of an interface from its observation type and FQN, with one parser per type:
 * <ul>
 *     <li>{@code event}, {@code command}: a {@link Message}, the FQN being {@code <messageType>[/<variant>]}</li>
 *     <li>{@code http_request}: an {@link HttpOperation}, the FQN being {@code <provider> <METHOD> <pattern>}</li>
 *     <li>any other type: a {@link GenericInterface}</li>
 * </ul>
 * <p>
 * <b>Never throws.</b> An FQN the parser of its type cannot parse becomes a {@link GenericInterface} as well, and is
 * counted in {@link ReactionObserverMetrics#unparsableInterface} - one interface reported wrongly must not break the
 * graph of the whole landscape.
 * <p>
 * An unparsable interface is counted and logged <b>once per interface and instance</b>: the graph is rebuilt on every
 * refresh, and counting it again on each of them would measure the refresh schedule rather than the interfaces.
 */
@Slf4j
public class InterfaceNodeFactory {

    static final String TYPE_EVENT = "event";
    static final String TYPE_COMMAND = "command";
    static final String TYPE_HTTP_REQUEST = "http_request";

    private final ReactionObserverMetrics metrics;
    private final Set<Long> reportedUnparsableInterfaces = ConcurrentHashMap.newKeySet();

    public InterfaceNodeFactory(ReactionObserverMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * @param id   the id of the interface, which is its identity
     * @param type the observation type
     * @param fqn  the fully qualified name of the interface
     */
    public Interface create(long id, String type, String fqn) {
        String normalizedType = type == null ? "" : type.toLowerCase(Locale.ROOT);
        Optional<Interface> parsed = switch (normalizedType) {
            case TYPE_EVENT, TYPE_COMMAND -> parseMessage(id, type, fqn);
            case TYPE_HTTP_REQUEST -> parseHttpOperation(id, fqn);
            // Not an error: a type the observer does not know yet is presented as it was reported
            default -> Optional.of(new GenericInterface(id, type, fqn));
        };
        return parsed.orElseGet(() -> unparsable(id, type, fqn));
    }

    /**
     * {@code <messageType>} or {@code <messageType>/<variant>}, split as it always was: further segments are
     * ignored, as they were before there was a factory, so that no existing message node changes.
     */
    private static Optional<Interface> parseMessage(long id, String type, String fqn) {
        if (fqn == null) {
            return Optional.empty();
        }
        String[] parts = fqn.contains("/") ? fqn.split("/") : new String[]{fqn};
        if (parts.length == 0 || parts[0].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Message.builder()
                .id(id)
                .messageType(parts[0])
                .variant(parts.length > 1 ? parts[1] : null)
                .semantic(SemanticType.fromValue(type).orElseThrow())
                .build());
    }

    /**
     * {@code <provider> <METHOD> <pattern>}, split at the first two spaces. A stray space right after
     * {@code METHOD} (or at the end of the FQN) would otherwise leak into {@code pattern} unnoticed, since the
     * third part is the raw remainder and is not split again: rejected the same way as a stray space before
     * {@code METHOD} already is, instead of being parsed into a path with a leading or trailing space.
     */
    private static Optional<Interface> parseHttpOperation(long id, String fqn) {
        if (fqn == null) {
            return Optional.empty();
        }
        String[] parts = fqn.split(" ", 3);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()
                || !parts[2].equals(parts[2].strip())) {
            return Optional.empty();
        }
        return Optional.of(HttpOperation.builder()
                .id(id)
                .provider(parts[0])
                .method(parts[1])
                .path(parts[2])
                .build());
    }

    private GenericInterface unparsable(long id, String type, String fqn) {
        if (reportedUnparsableInterfaces.add(id)) {
            log.warn("The FQN '{}' of the interface {} of type '{}' cannot be parsed, it is presented as a generic " +
                     "interface", fqn, id, type);
            metrics.unparsableInterface(type);
        }
        return new GenericInterface(id, type, fqn);
    }
}
