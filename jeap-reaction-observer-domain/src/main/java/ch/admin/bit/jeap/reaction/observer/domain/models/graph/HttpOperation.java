package ch.admin.bit.jeap.reaction.observer.domain.models.graph;

import lombok.Builder;

/**
 * Node representing an HTTP operation of a provider in the graph, observed as an {@code http_request}.
 * <p>
 * Parsed from the FQN {@code <provider> <METHOD> <pattern>}. Its identity is the interface it was parsed from,
 * i.e. its {@link #id()}, not the parsed parts.
 *
 * @param type     the observation type as it was reported; {@code http_request} when not given
 * @param fqn      the FQN as it was reported; {@code <provider> <METHOD> <pattern>} when not given
 * @param provider the service providing the operation; starts with {@code //} when the caller named the
 *                 operation because the called service did not, and then is where the call went, e.g.
 *                 {@code //foo.bar.admin.ch}
 * @param method   the HTTP method, e.g. {@code GET}
 * @param path     the path pattern of the operation, e.g. {@code /api/orders/{id}}
 */
@Builder
public record HttpOperation(
        long id,
        String type,
        String fqn,
        String provider,
        String method,
        String path
) implements Interface {

    public static final String OBSERVATION_TYPE = "http_request";

    public HttpOperation(long id, String type, String fqn, String provider, String method, String path) {
        this.id = id;
        this.type = type != null ? type : OBSERVATION_TYPE;
        this.fqn = fqn != null ? fqn : provider + " " + method + " " + path;
        this.provider = provider;
        this.method = method;
        this.path = path;
    }

    /** An HTTP operation whose observation type and FQN are derived from its parts. */
    public HttpOperation(long id, String provider, String method, String path) {
        this(id, null, null, provider, method, path);
    }
}
