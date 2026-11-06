package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

import lombok.Builder;

/**
 * Node representing an HTTP operation of a provider in the graph, observed as an {@code http_request}.
 * <p>
 * Parsed from the FQN {@code <provider> <METHOD> <pattern>}. Its identity is the interface it was parsed from,
 * i.e. its {@link #id()}, not the parsed parts.
 *
 * @param provider the service providing the operation
 * @param method   the HTTP method, e.g. {@code GET}
 * @param path     the path pattern of the operation, e.g. {@code /api/orders/{id}}
 */
@Builder
public record HttpOperation(
        long id,
        String provider,
        String method,
        String path
) implements Interface {
}
