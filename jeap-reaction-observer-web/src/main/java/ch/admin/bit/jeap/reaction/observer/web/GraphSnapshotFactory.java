package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphDtoMapper;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds a {@link GraphSnapshot}: every subgraph is extracted and fingerprinted once, when the graph is
 * refreshed.
 * <p>
 * <b>This is the cost of the indexes</b>, and it is paid on the refresh schedule rather than per request -
 * where the same work used to be done again for every single call. A run logs how long it took and how much
 * it produced, because it is the one part of a refresh that grows with the landscape.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphSnapshotFactory {

    private final GraphExtractor graphExtractor;
    private final GraphFingerprintCalculator fingerprintCalculator;

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
        return new GraphSnapshot(graph, graphFingerprint, systems, components, messageTypes);
    }

    /**
     * One entry per system that has a reaction, so the index lists what the graph resource answers with
     * {@code 200} and nothing else - a name whose subgraph would be empty is not in the graph to begin with.
     */
    private List<GraphSnapshot.SystemEntry> systemEntries(Graph graph) {
        return reactions(graph).stream()
                .map(Reaction::system)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(system -> new GraphSnapshot.SystemEntry(system,
                        fingerprintOf(graphExtractor.getSystemRelatedGraph(graph, system))))
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
                    fingerprintOf(graphExtractor.getComponentRelatedGraph(graph, component))));
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
        Map<String, List<String>> variantsByType = new TreeMap<>();
        graph.nodes().stream()
                .filter(Message.class::isInstance)
                .map(Message.class::cast)
                .forEach(message -> variantsByType
                        .computeIfAbsent(message.messageType(), type -> new ArrayList<>())
                        .add(message.variant()));

        List<GraphSnapshot.MessageTypeEntry> entries = new ArrayList<>();
        variantsByType.forEach((messageType, variants) -> {
            Map<String, String> fingerprintsByKey = new TreeMap<>();
            variants.stream().distinct().forEach(variant -> fingerprintsByKey.put(
                    MessageGraphKey.of(messageType, variant),
                    fingerprintOf(graphExtractor.getMessageRelatedGraph(graph, messageType, variant))));
            entries.add(new GraphSnapshot.MessageTypeEntry(messageType,
                    List.copyOf(fingerprintsByKey.keySet()),
                    fingerprintCalculator.combine(fingerprintsByKey)));
        });
        return entries;
    }

    private String fingerprintOf(Graph subgraph) {
        return fingerprintCalculator.calculate(GraphDtoMapper.map(subgraph));
    }

    private static List<Reaction> reactions(Graph graph) {
        return graph.nodes().stream()
                .filter(Reaction.class::isInstance)
                .map(Reaction.class::cast)
                .toList();
    }
}
