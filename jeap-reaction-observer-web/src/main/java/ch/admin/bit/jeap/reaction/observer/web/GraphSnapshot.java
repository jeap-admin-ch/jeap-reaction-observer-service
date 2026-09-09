package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The graph an instance serves from, together with the fingerprint of <b>every subgraph that can be asked
 * for</b> - computed once when the graph is refreshed rather than once per request.
 * <p>
 * That is what makes the indexes affordable and a conditional request cheap: an index is a lookup rather than
 * an extraction of every subgraph, and an {@code If-None-Match} that matches is answered without extracting,
 * serializing or hashing anything.
 * <p>
 * <b>Immutable, and replaced whole.</b> A refresh builds a new snapshot and swaps it in, so a request is
 * always answered from one consistent graph - never from a graph that is being rebuilt under it.
 *
 * @param graph            the graph itself, filtered to the statistics period, as the resources answer it
 * @param graphFingerprint the fingerprint of the whole graph
 * @param systems          one entry per system that has a reaction in this graph, by name
 * @param components       one entry per component that has a reaction in this graph, by name
 * @param messageTypes     one entry per message type that appears in this graph
 */
public record GraphSnapshot(Graph graph,
                            String graphFingerprint,
                            List<SystemEntry> systems,
                            List<ComponentEntry> components,
                            List<MessageTypeEntry> messageTypes) {

    /** A system with a reaction, and the fingerprint of its subgraph. */
    public record SystemEntry(String name, String fingerprint) {
    }

    /**
     * A component with a reaction, the system its reactions were published under, and the fingerprint of its
     * subgraph.
     */
    public record ComponentEntry(String name, String system, String fingerprint) {
    }

    /**
     * A message type, the variants of it this graph holds - as the keys the graph resource answers with, see
     * {@link MessageGraphKey} - and one fingerprint over all of them, because the resource answers all of them
     * at once.
     */
    public record MessageTypeEntry(String messageType, List<String> variantKeys, String fingerprint) {
    }

    public GraphSnapshot {
        systems = systems == null ? List.of() : List.copyOf(systems);
        components = components == null ? List.of() : List.copyOf(components);
        messageTypes = messageTypes == null ? List.of() : List.copyOf(messageTypes);
    }

    /** What an instance serves before its first refresh, so that nothing has to handle a missing graph. */
    public static GraphSnapshot empty() {
        return new GraphSnapshot(new Graph(List.of(), List.of()), null, List.of(), List.of(), List.of());
    }

    /**
     * The fingerprint of one system's subgraph, or null when this graph has no reaction of that system.
     * <p>
     * <b>Matched ignoring case</b>, because that is how {@code GraphExtractor} matches a system - and the
     * names in the graph are lower-cased by the observer when a reaction is stored, while a caller may well
     * ask with the spelling its own model uses.
     */
    public String fingerprintOfSystem(String name) {
        return name == null ? null : byLowerCasedName(systems).get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * The fingerprint of one component's subgraph, or null when this graph has no reaction of that component.
     * <p>
     * <b>Matched exactly</b>, again because that is what {@code GraphExtractor} does with a component name.
     */
    public String fingerprintOfComponent(String name) {
        return components.stream()
                .filter(entry -> entry.name().equals(name))
                .map(ComponentEntry::fingerprint)
                .findFirst()
                .orElse(null);
    }

    /** The fingerprint over every variant of one message type, or null when this graph has none of it. */
    public String fingerprintOfMessageType(String messageType) {
        return messageTypes.stream()
                .filter(entry -> entry.messageType().equals(messageType))
                .map(MessageTypeEntry::fingerprint)
                .findFirst()
                .orElse(null);
    }

    private static Map<String, String> byLowerCasedName(List<SystemEntry> entries) {
        Map<String, String> byName = new LinkedHashMap<>();
        entries.forEach(entry -> byName.put(entry.name().toLowerCase(Locale.ROOT), entry.fingerprint()));
        return byName;
    }
}
