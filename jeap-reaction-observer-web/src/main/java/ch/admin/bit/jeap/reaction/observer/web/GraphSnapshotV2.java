package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshot.IndexPayload;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * What the API v2 serves from a {@link GraphSnapshot}, derived from its full graph: the fingerprint of every
 * subgraph that can be asked for, and the indexes, serialized and tagged.
 * <p>
 * The counterpart of what the snapshot holds for the API v1, and separate from it: the two versions serve different
 * views of the graph, so their fingerprints and entity tags are independent of each other.
 *
 * @param graphFingerprint        the fingerprint of the whole graph, or null before the first refresh
 * @param fingerprintsBySystem    by the lower-cased system name, because a system is matched ignoring case
 * @param fingerprintsByComponent by the exact component name
 * @param fingerprintsByInterface by the database id of the interface
 * @param systemIndex             the index of the system graphs, or null before the first refresh
 * @param componentIndex          the index of the component graphs, or null before the first refresh
 * @param interfaceIndex          the index of the graphs of all interfaces, or null before the first refresh
 * @param interfaceIndexesByType  the index of the graphs of the interfaces of one type, by that type
 * @param emptyInterfaceIndex     the index of a type the graph has no interface of, or null before the first refresh
 */
public record GraphSnapshotV2(@Nullable String graphFingerprint,
                              Map<String, String> fingerprintsBySystem,
                              Map<String, String> fingerprintsByComponent,
                              Map<Long, String> fingerprintsByInterface,
                              @Nullable IndexPayload systemIndex,
                              @Nullable IndexPayload componentIndex,
                              @Nullable IndexPayload interfaceIndex,
                              Map<String, IndexPayload> interfaceIndexesByType,
                              @Nullable IndexPayload emptyInterfaceIndex) {

    public GraphSnapshotV2 {
        fingerprintsBySystem = Map.copyOf(fingerprintsBySystem);
        fingerprintsByComponent = Map.copyOf(fingerprintsByComponent);
        fingerprintsByInterface = Map.copyOf(fingerprintsByInterface);
        interfaceIndexesByType = Map.copyOf(interfaceIndexesByType);
    }

    /** What an instance serves before its first refresh. */
    static GraphSnapshotV2 empty() {
        return new GraphSnapshotV2(null, Map.of(), Map.of(), Map.of(), null, null, null, Map.of(), null);
    }

    /** The fingerprint of one system's subgraph, matched ignoring case, or null when there is none. */
    public @Nullable String fingerprintOfSystem(@Nullable String name) {
        return name == null ? null : fingerprintsBySystem.get(name.toLowerCase(Locale.ROOT));
    }

    /** The fingerprint of one component's subgraph, matched exactly, or null when there is none. */
    public @Nullable String fingerprintOfComponent(@Nullable String name) {
        return name == null ? null : fingerprintsByComponent.get(name);
    }

    /** The fingerprint of the subgraph of one interface, or null when the graph has no such interface. */
    public @Nullable String fingerprintOfInterface(long interfaceId) {
        return fingerprintsByInterface.get(interfaceId);
    }

    /**
     * The index of the interface graphs: of all interfaces, or of the interfaces of one type.
     *
     * @param interfaceType the type as it is reported, matched exactly, or null for all interfaces
     * @return the index - an empty one for a type the graph has no interface of - or null before the first refresh
     */
    public @Nullable IndexPayload interfaceIndex(@Nullable String interfaceType) {
        if (interfaceType == null) {
            return interfaceIndex;
        }
        return interfaceIndexesByType.getOrDefault(interfaceType, emptyInterfaceIndex);
    }
}
