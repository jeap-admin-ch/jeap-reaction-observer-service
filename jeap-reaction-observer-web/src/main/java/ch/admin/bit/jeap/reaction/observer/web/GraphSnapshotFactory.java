package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;
import ch.admin.bit.jeap.reaction.observer.web.api.EtagSupport;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphIndexDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphIndexDto.GraphIndexEntryDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.MessageGraphIndexDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.MessageGraphIndexDto.MessageGraphIndexEntryDto;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphDtoMapper;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

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

    public GraphSnapshotFactory(GraphExtractor graphExtractor,
                                GraphFingerprintCalculator fingerprintCalculator,
                                EtagSupport etagSupport,
                                @Value("${server.servlet.context-path:}") String contextPath) {
        this.graphExtractor = graphExtractor;
        this.fingerprintCalculator = fingerprintCalculator;
        this.etagSupport = etagSupport;
        this.apiRoot = normalized(contextPath) + "/api/graphs";
    }

    @Timed("reaction_observer_service_graph_snapshot")
    public GraphSnapshot of(Graph graph) {
        if (graph == null) {
            return GraphSnapshot.empty();
        }
        Instant startedAt = Instant.now();
        String graphFingerprint = fingerprintCalculator.calculate(GraphDtoMapper.map(graph));
        List<GraphSnapshot.SystemEntry> systems = systemEntries(graph);
        List<GraphSnapshot.ComponentEntry> components = componentEntries(graph);
        List<GraphSnapshot.MessageTypeEntry> messageTypes = messageTypeEntries(graph);
        log.info("Indexed the reaction graph: {} systems, {} components, {} message types ({})",
                systems.size(), components.size(), messageTypes.size(),
                Duration.between(startedAt, Instant.now()));
        return GraphSnapshot.of(graph, graphFingerprint, systems, components, messageTypes,
                systemIndex(systems), componentIndex(components), messageIndex(messageTypes));
    }

    /**
     * One entry per system that has a reaction, so the index lists what the graph resource answers with
     * {@code 200} and nothing else - a name whose subgraph would be empty is not in the graph to begin with.
     */
    private List<GraphSnapshot.SystemEntry> systemEntries(Graph graph) {
        Map<String, Graph> subgraphs = graphExtractor.getSystemRelatedGraphs(graph);
        return reactions(graph).stream()
                .map(Reaction::system)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(system -> new GraphSnapshot.SystemEntry(system,
                        fingerprintOf(subgraphs.get(system.toLowerCase(Locale.ROOT)))))
                .toList();
    }

    /**
     * One entry per component, with the system its reactions carry.
     * <p>
     * A component is expected to belong to one system; if the graph says otherwise the first system by name is
     * taken and the conflict is logged, because a component that reports two systems is a producer-side defect
     * and not something a reader of the index can act on.
     */
    private List<GraphSnapshot.ComponentEntry> componentEntries(Graph graph) {
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
                    fingerprintOf(subgraphs.get(component))));
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

    private GraphSnapshot.IndexPayload systemIndex(List<GraphSnapshot.SystemEntry> systems) {
        return payload(new GraphIndexDto(systems.stream()
                .map(entry -> new GraphIndexEntryDto(entry.name(), null,
                        etagSupport.entityTag(entry.fingerprint()),
                        apiRoot + "/systems/" + encode(entry.name())))
                .toList()));
    }

    private GraphSnapshot.IndexPayload componentIndex(List<GraphSnapshot.ComponentEntry> components) {
        return payload(new GraphIndexDto(components.stream()
                .map(entry -> new GraphIndexEntryDto(entry.name(), entry.system(),
                        etagSupport.entityTag(entry.fingerprint()),
                        apiRoot + "/components/" + encode(entry.name())))
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
