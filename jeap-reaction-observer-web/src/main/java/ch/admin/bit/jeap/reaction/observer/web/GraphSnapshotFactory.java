package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.domain.MessageGraphFilter;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Interface;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;
import ch.admin.bit.jeap.reaction.observer.web.api.EtagSupport;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphIndexDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphIndexDto.GraphIndexEntryDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.MessageGraphIndexDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.MessageGraphIndexDto.MessageGraphIndexEntryDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.InterfaceGraphIndexDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.InterfaceGraphIndexDto.InterfaceGraphIndexEntryDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.NodeV2Id;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphDtoMapper;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphV2DtoMapper;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Builds a {@link GraphSnapshot}: every subgraph is extracted and fingerprinted, and the three indexes are
 * serialized and tagged, <b>once</b> - when the graph is refreshed.
 * <p>
 * This is the cost of the indexes, and it is paid on the refresh schedule rather than per request, where the
 * same work used to be redone for every single call. It is measured
 * ({@code reaction_observer_service_graph_snapshot}) and logged, because it is the one part of a refresh that
 * grows with the landscape.
 * <p>
 * The extraction is the <b>batched</b> form of {@code GraphExtractor}: one pass over the graph per kind
 * instead of one per name. The per-name form is what the graph resources answer with, and the two are held
 * equal by {@code GraphExtractorTest} - which matters, because an index entry's tag has to be the one its
 * resource answers with.
 * <p>
 * The fingerprints and indexes of the API v1 are derived from the <b>view of the API v1</b>
 * ({@link MessageGraphFilter}), those of the API v2 from the full graph ({@link GraphSnapshotV2}). They are computed
 * separately, over the representation each version serves, so the entity tags of the two are independent.
 */
@Slf4j
@Component
public class GraphSnapshotFactory {

    private final GraphExtractor graphExtractor;
    private final GraphFingerprintCalculator fingerprintCalculator;
    private final EtagSupport etagSupport;

    /**
     * What the API is served under, which the {@code path} of an index entry has to carry: an entry is meant
     * to be usable as it stands, and this service runs under a context path.
     */
    private final String apiRoot;
    private final String apiRootV2;

    public GraphSnapshotFactory(GraphExtractor graphExtractor,
                                GraphFingerprintCalculator fingerprintCalculator,
                                EtagSupport etagSupport,
                                @Value("${server.servlet.context-path:}") String contextPath) {
        this.graphExtractor = graphExtractor;
        this.fingerprintCalculator = fingerprintCalculator;
        this.etagSupport = etagSupport;
        this.apiRoot = normalized(contextPath) + "/api/graphs";
        this.apiRootV2 = normalized(contextPath) + "/api/v2/graphs";
    }

    @Timed("reaction_observer_service_graph_snapshot")
    public GraphSnapshot of(Graph fullGraph) {
        if (fullGraph == null) {
            return GraphSnapshot.empty();
        }
        Instant startedAt = Instant.now();
        Graph graph = MessageGraphFilter.filter(fullGraph);
        String graphFingerprint = fingerprintCalculator.calculate(GraphDtoMapper.map(graph));
        List<GraphSnapshot.SystemEntry> systems = systemEntries(graph, this::fingerprintOf);
        List<GraphSnapshot.ComponentEntry> components = componentEntries(graph, this::fingerprintOf);
        List<GraphSnapshot.MessageTypeEntry> messageTypes = messageTypeEntries(graph);
        log.info("Indexed the reaction graph: {} systems, {} components, {} message types ({})",
                systems.size(), components.size(), messageTypes.size(),
                Duration.between(startedAt, Instant.now()));
        return GraphSnapshot.of(fullGraph, graph, graphFingerprint, systems, components, messageTypes,
                systemIndex(systems, apiRoot), componentIndex(components, apiRoot), messageIndex(messageTypes),
                v2(fullGraph));
    }

    /** The fingerprints and indexes of the API v2, over the full graph and its own representation of it. */
    private GraphSnapshotV2 v2(Graph fullGraph) {
        Instant startedAt = Instant.now();
        List<GraphSnapshot.SystemEntry> systems = systemEntries(fullGraph, this::fingerprintOfV2);
        List<GraphSnapshot.ComponentEntry> components = componentEntries(fullGraph, this::fingerprintOfV2);
        List<InterfaceEntry> interfaces = interfaceEntries(fullGraph);

        Map<String, String> fingerprintsBySystem = new HashMap<>();
        systems.forEach(entry -> fingerprintsBySystem.put(entry.name().toLowerCase(Locale.ROOT), entry.fingerprint()));
        Map<String, String> fingerprintsByComponent = new HashMap<>();
        components.forEach(entry -> fingerprintsByComponent.put(entry.name(), entry.fingerprint()));
        Map<Long, String> fingerprintsByInterface = new HashMap<>();
        interfaces.forEach(entry -> fingerprintsByInterface.put(entry.id(), entry.fingerprint()));

        // An interface without a type is in the index of all interfaces only: there is no type to ask for it by
        Map<String, List<InterfaceEntry>> interfacesByType = new TreeMap<>();
        interfaces.stream()
                .filter(entry -> entry.type() != null)
                .forEach(entry -> interfacesByType.computeIfAbsent(entry.type(), type -> new ArrayList<>())
                        .add(entry));
        Map<String, GraphSnapshot.IndexPayload> interfaceIndexesByType = new HashMap<>();
        interfacesByType.forEach((type, entries) -> interfaceIndexesByType.put(type, interfaceIndex(entries)));

        log.info("Indexed the reaction graph for the API v2: {} systems, {} components, {} interfaces of the types "
                 + "{} ({})", systems.size(), components.size(), interfaces.size(), interfacesByType.keySet(),
                Duration.between(startedAt, Instant.now()));
        return new GraphSnapshotV2(fingerprintOfV2(fullGraph), fingerprintsBySystem, fingerprintsByComponent,
                fingerprintsByInterface, systemIndex(systems, apiRootV2), componentIndex(components, apiRootV2),
                interfaceIndex(interfaces), interfaceIndexesByType, interfaceIndex(List.of()));
    }

    private static final Comparator<String> NULLS_FIRST = Comparator.nullsFirst(Comparator.naturalOrder());

    /** An interface of the full graph, and the fingerprint of its subgraph. */
    private record InterfaceEntry(long id, String type, String fqn, String fingerprint) {
    }

    /**
     * One entry per interface of the full graph, ordered by type and FQN. Every interface of the graph belongs to
     * a reaction, so its subgraph is never empty.
     * <p>
     * A stored interface always has a type and an FQN. A graph handed to {@link GraphHolder#setGraph(Graph)} by
     * other means may have interfaces without, which are listed first and must not fail the snapshot - it is the
     * one every endpoint answers from.
     */
    private List<InterfaceEntry> interfaceEntries(Graph fullGraph) {
        Map<Long, Graph> subgraphs = graphExtractor.getInterfaceRelatedGraphs(fullGraph);
        return fullGraph.nodes().stream()
                .filter(Interface.class::isInstance)
                .map(Interface.class::cast)
                .map(iface -> new InterfaceEntry(iface.id(), iface.type(), iface.fqn(),
                        fingerprintOfV2(subgraphs.get(iface.id()))))
                .sorted(Comparator.comparing(InterfaceEntry::type, NULLS_FIRST)
                        .thenComparing(InterfaceEntry::fqn, NULLS_FIRST)
                        .thenComparingLong(InterfaceEntry::id))
                .toList();
    }

    private GraphSnapshot.IndexPayload interfaceIndex(List<InterfaceEntry> interfaces) {
        return payload(new InterfaceGraphIndexDto(interfaces.stream()
                .map(entry -> {
                    String nodeId = NodeV2Id.ofInterface(entry.id());
                    return new InterfaceGraphIndexEntryDto(nodeId, entry.type(), entry.fqn(),
                            etagSupport.entityTag(entry.fingerprint()), apiRootV2 + "/interfaces/" + nodeId);
                })
                .toList()));
    }

    /**
     * One entry per system that has a reaction, so the index lists what the graph resource answers with
     * {@code 200} and nothing else - a name whose subgraph would be empty is not in the graph to begin with.
     */
    private List<GraphSnapshot.SystemEntry> systemEntries(Graph graph, Function<Graph, String> fingerprintOf) {
        Map<String, Graph> subgraphs = graphExtractor.getSystemRelatedGraphs(graph);
        return reactions(graph).stream()
                .map(Reaction::system)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(system -> new GraphSnapshot.SystemEntry(system,
                        fingerprintOf.apply(subgraphs.get(system.toLowerCase(Locale.ROOT)))))
                .toList();
    }

    /**
     * One entry per component, with the system its reactions carry.
     * <p>
     * A component is expected to belong to one system; if the graph says otherwise the first system by name is
     * taken and the conflict is logged, because a component that reports two systems is a producer-side defect
     * and not something a reader of the index can act on.
     */
    private List<GraphSnapshot.ComponentEntry> componentEntries(Graph graph,
                                                                Function<Graph, String> fingerprintOf) {
        Map<String, Graph> subgraphs = graphExtractor.getComponentRelatedGraphs(graph);
        Map<String, Set<String>> systemsByComponent = new LinkedHashMap<>();
        reactions(graph).forEach(reaction -> systemsByComponent
                .computeIfAbsent(reaction.component(), component -> new LinkedHashSet<>())
                .add(reaction.system()));

        List<GraphSnapshot.ComponentEntry> entries = new ArrayList<>();
        systemsByComponent.forEach((component, systems) -> {
            if (systems.size() > 1) {
                log.warn("The component '{}' has reactions of more than one system: {}. The first one is " +
                         "indexed.", component, systems);
            }
            String system = systems.stream().filter(Objects::nonNull).sorted().findFirst().orElse(null);
            entries.add(new GraphSnapshot.ComponentEntry(component, system,
                    fingerprintOf.apply(subgraphs.get(component))));
        });
        entries.sort(Comparator.comparing(GraphSnapshot.ComponentEntry::name));
        return entries;
    }

    /**
     * One entry per message type, with its variants as the keys the graph resource answers with, and one
     * fingerprint over all of them.
     * <p>
     * The fingerprint is over the variants and their own fingerprints rather than over one merged graph: the
     * resource answers a map of variant to graph, so what a consumer compares has to cover the whole map - a
     * variant that appears or disappears has to move the tag even when no graph of the type changed.
     */
    private List<GraphSnapshot.MessageTypeEntry> messageTypeEntries(Graph graph) {
        Map<GraphExtractor.MessageKey, Graph> subgraphs = graphExtractor.getMessageRelatedGraphs(graph);

        Map<String, Map<String, String>> fingerprintsByType = new TreeMap<>();
        subgraphs.forEach((key, subgraph) -> fingerprintsByType
                .computeIfAbsent(key.messageType(), type -> new TreeMap<>())
                .put(MessageGraphKey.of(key.messageType(), key.variant()), fingerprintOf(subgraph)));

        List<GraphSnapshot.MessageTypeEntry> entries = new ArrayList<>();
        fingerprintsByType.forEach((messageType, fingerprintsByKey) ->
                entries.add(new GraphSnapshot.MessageTypeEntry(messageType,
                        List.copyOf(fingerprintsByKey.keySet()),
                        fingerprintCalculator.combine(fingerprintsByKey))));
        return entries;
    }

    private GraphSnapshot.IndexPayload systemIndex(List<GraphSnapshot.SystemEntry> systems, String root) {
        return payload(new GraphIndexDto(systems.stream()
                .map(entry -> new GraphIndexEntryDto(entry.name(), null,
                        etagSupport.entityTag(entry.fingerprint()),
                        root + "/systems/" + encode(entry.name())))
                .toList()));
    }

    private GraphSnapshot.IndexPayload componentIndex(List<GraphSnapshot.ComponentEntry> components,
                                                      String root) {
        return payload(new GraphIndexDto(components.stream()
                .map(entry -> new GraphIndexEntryDto(entry.name(), entry.system(),
                        etagSupport.entityTag(entry.fingerprint()),
                        root + "/components/" + encode(entry.name())))
                .toList()));
    }

    private GraphSnapshot.IndexPayload messageIndex(List<GraphSnapshot.MessageTypeEntry> messageTypes) {
        return payload(new MessageGraphIndexDto(messageTypes.stream()
                .map(entry -> new MessageGraphIndexEntryDto(entry.messageType(), entry.variantKeys(),
                        etagSupport.entityTag(entry.fingerprint()),
                        apiRoot + "/messages/" + encode(entry.messageType())))
                .toList()));
    }

    /** An index as it will be written, and its tag over exactly those bytes. */
    private GraphSnapshot.IndexPayload payload(Object index) {
        byte[] bytes = etagSupport.serialize(index);
        return new GraphSnapshot.IndexPayload(bytes, etagSupport.entityTagOf(bytes));
    }

    private String fingerprintOf(Graph subgraph) {
        Graph graph = subgraph == null ? new Graph(List.of(), List.of()) : subgraph;
        return fingerprintCalculator.calculate(GraphDtoMapper.map(graph));
    }

    private String fingerprintOfV2(Graph subgraph) {
        Graph graph = subgraph == null ? new Graph(List.of(), List.of()) : subgraph;
        return fingerprintCalculator.calculate(GraphV2DtoMapper.map(graph));
    }

    private static List<Reaction> reactions(Graph graph) {
        return graph.nodes().stream()
                .filter(Reaction.class::isInstance)
                .map(Reaction.class::cast)
                .toList();
    }

    /**
     * A name as a path segment. Component names are service names and message types are identifiers, so this
     * changes nothing today - and it is what keeps the path usable if one of them ever carries a character
     * that has to be escaped.
     */
    private static String encode(String name) {
        return UriUtils.encodePathSegment(name, StandardCharsets.UTF_8);
    }

    /** A context path without its trailing slash, and empty when there is none. */
    private static String normalized(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "";
        }
        String withLeadingSlash = contextPath.startsWith("/") ? contextPath : "/" + contextPath;
        return withLeadingSlash.endsWith("/")
                ? withLeadingSlash.substring(0, withLeadingSlash.length() - 1)
                : withLeadingSlash;
    }
}
