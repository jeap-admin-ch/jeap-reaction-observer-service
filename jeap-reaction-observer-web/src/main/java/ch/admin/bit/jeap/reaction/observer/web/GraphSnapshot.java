package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The graph an instance serves from, together with everything derived from it that a request would otherwise
 * have to derive again: the fingerprint of <b>every subgraph that can be asked for</b>, and the three
 * replication indexes, serialized and tagged.
 * <p>
 * That is what makes the indexes affordable and a conditional request cheap: an index is a lookup rather than
 * an extraction of every subgraph, and an {@code If-None-Match} that matches is answered without extracting,
 * serializing or hashing anything.
 * <p>
 * <b>Immutable, and replaced whole.</b> A refresh builds a new snapshot and swaps it in, so a request is
 * always answered from one consistent graph - never from a graph that is being rebuilt under it. A handler
 * therefore has to read the snapshot <b>once</b> and answer everything from that one object; reading it twice
 * can straddle a refresh and tag one graph with another's fingerprint.
 */
public final class GraphSnapshot {

    private final Graph graph;
    private final String graphFingerprint;
    private final List<SystemEntry> systems;
    private final List<ComponentEntry> components;
    private final List<MessageTypeEntry> messageTypes;
    private final IndexPayload systemIndex;
    private final IndexPayload componentIndex;
    private final IndexPayload messageIndex;

    /** Lower-cased, because {@code GraphExtractor} matches a system name ignoring case. */
    private final Map<String, String> fingerprintsBySystem;
    /** Exact, because {@code GraphExtractor} matches a component name exactly. */
    private final Map<String, String> fingerprintsByComponent;
    private final Map<String, String> fingerprintsByMessageType;

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

    /**
     * One index as it goes on the wire, and its entity tag over exactly those bytes.
     * <p>
     * {@code equals}, {@code hashCode} and {@code toString} are written out because a record holding an array
     * would compare it by identity: two payloads of the same index would then be unequal, which is the
     * opposite of what a reader expects. The tag is a hash of the bytes, so comparing both is comparing the
     * content twice - deliberately, so that the pair cannot be equal while disagreeing.
     *
     * @param bytes the serialized index. Not copied: this object is created once per refresh and only ever
     *              written to a response
     */
    public record IndexPayload(byte[] bytes, String etag) {

        @Override
        public boolean equals(Object other) {
            return other instanceof IndexPayload(byte[] otherBytes, String otherEtag)
                   && Objects.equals(etag, otherEtag)
                   && Arrays.equals(bytes, otherBytes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(etag, Arrays.hashCode(bytes));
        }

        @Override
        public String toString() {
            return "IndexPayload[etag=" + etag + ", bytes=" + (bytes == null ? 0 : bytes.length) + " bytes]";
        }
    }

    @SuppressWarnings("java:S107") // Seven parts of one snapshot; the factory is the only caller
    private GraphSnapshot(Graph graph, String graphFingerprint, List<SystemEntry> systems,
                          List<ComponentEntry> components, List<MessageTypeEntry> messageTypes,
                          IndexPayload systemIndex, IndexPayload componentIndex, IndexPayload messageIndex) {
        this.graph = graph;
        this.graphFingerprint = graphFingerprint;
        this.systems = List.copyOf(systems);
        this.components = List.copyOf(components);
        this.messageTypes = List.copyOf(messageTypes);
        this.systemIndex = systemIndex;
        this.componentIndex = componentIndex;
        this.messageIndex = messageIndex;
        this.fingerprintsBySystem = lookup(systems, entry -> entry.name().toLowerCase(Locale.ROOT),
                SystemEntry::fingerprint);
        this.fingerprintsByComponent = lookup(components, ComponentEntry::name, ComponentEntry::fingerprint);
        this.fingerprintsByMessageType = lookup(messageTypes, MessageTypeEntry::messageType,
                MessageTypeEntry::fingerprint);
    }

    @SuppressWarnings("java:S107")
    static GraphSnapshot of(Graph graph, String graphFingerprint, List<SystemEntry> systems,
                            List<ComponentEntry> components, List<MessageTypeEntry> messageTypes,
                            IndexPayload systemIndex, IndexPayload componentIndex,
                            IndexPayload messageIndex) {
        return new GraphSnapshot(graph, graphFingerprint, systems, components, messageTypes, systemIndex,
                componentIndex, messageIndex);
    }

    /** What an instance serves before its first refresh, so that nothing has to handle a missing graph. */
    public static GraphSnapshot empty() {
        return new GraphSnapshot(new Graph(List.of(), List.of()), null, List.of(), List.of(), List.of(),
                null, null, null);
    }

    public Graph graph() {
        return graph;
    }

    public @Nullable String graphFingerprint() {
        return graphFingerprint;
    }

    public List<SystemEntry> systems() {
        return systems;
    }

    public List<ComponentEntry> components() {
        return components;
    }

    public List<MessageTypeEntry> messageTypes() {
        return messageTypes;
    }

    /** The index of the system graphs, or null before the first refresh. */
    public @Nullable IndexPayload systemIndex() {
        return systemIndex;
    }

    public @Nullable IndexPayload componentIndex() {
        return componentIndex;
    }

    public @Nullable IndexPayload messageIndex() {
        return messageIndex;
    }

    /**
     * The fingerprint of one system's subgraph, or null when this graph has no reaction of that system.
     * <p>
     * <b>Matched ignoring case</b>, because that is how {@code GraphExtractor} matches a system - and the
     * names in the graph are lower-cased by the observer when a reaction is stored, while a caller may well
     * ask with the spelling its own model uses.
     */
    public @Nullable String fingerprintOfSystem(@Nullable String name) {
        return name == null ? null : fingerprintsBySystem.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * The fingerprint of one component's subgraph, or null when this graph has no reaction of that component.
     * <p>
     * <b>Matched exactly</b>, again because that is what {@code GraphExtractor} does with a component name.
     */
    public @Nullable String fingerprintOfComponent(@Nullable String name) {
        return name == null ? null : fingerprintsByComponent.get(name);
    }

    /** The fingerprint over every variant of one message type, or null when this graph has none of it. */
    public @Nullable String fingerprintOfMessageType(@Nullable String messageType) {
        return messageType == null ? null : fingerprintsByMessageType.get(messageType);
    }

    private static <T> Map<String, String> lookup(List<T> entries,
                                                  java.util.function.Function<T, String> keyOf,
                                                  java.util.function.Function<T, String> valueOf) {
        Map<String, String> byKey = new LinkedHashMap<>();
        entries.forEach(entry -> byKey.put(keyOf.apply(entry), valueOf.apply(entry)));
        return Map.copyOf(byKey);
    }
}
