package ch.admin.bit.jeap.reaction.observer.service.test.model;

import java.util.SortedMap;
import java.util.TreeMap;

public record TestObservation(String type, String fqn, SortedMap<String, String> props, String id) {

    public static final String TYPE_HTTP_REQUEST = "http_request";

    public static TestObservation ofCommand(String messageType) {
        return new TestObservation("command", messageType, new TreeMap<>(), "command:" + messageType);
    }

    public static TestObservation ofEvent(String messageType) {
        return new TestObservation("event", messageType, new TreeMap<>(), "event:" + messageType);
    }

    /**
     * An HTTP request to an operation of a provider, as the reaction observer library reports it: the FQN is
     * {@code <provider> <METHOD> <pattern>}, there are no props, and the id is built the way the library builds it.
     *
     * @param provider the service providing the operation, e.g. {@code order-service}
     * @param method   the HTTP method, e.g. {@code POST}
     * @param pattern  the path pattern of the operation, e.g. {@code /api/orders/{id}}
     */
    public static TestObservation ofHttpRequest(String provider, String method, String pattern) {
        return ofType(TYPE_HTTP_REQUEST, provider + " " + method + " " + pattern);
    }

    /**
     * An observation of any type, e.g. one the reaction observer service does not know, with no props and the id
     * {@code <type>:<encoded fqn>} - see {@link #encode(String)}.
     */
    public static TestObservation ofType(String type, String fqn) {
        return new TestObservation(type, fqn, new TreeMap<>(), type + ":" + encode(fqn));
    }

    /**
     * Encodes an FQN for an observation id as the reaction observer library does: {@code %}, {@code :}, {@code #}
     * and {@code &} are percent-encoded, everything else (including spaces and {@code /}) is left as it is.
     */
    public static String encode(String fqn) {
        StringBuilder encoded = new StringBuilder(fqn.length());
        for (char c : fqn.toCharArray()) {
            switch (c) {
                case '%' -> encoded.append("%25");
                case ':' -> encoded.append("%3A");
                case '#' -> encoded.append("%23");
                case '&' -> encoded.append("%26");
                default -> encoded.append(c);
            }
        }
        return encoded.toString();
    }
}
