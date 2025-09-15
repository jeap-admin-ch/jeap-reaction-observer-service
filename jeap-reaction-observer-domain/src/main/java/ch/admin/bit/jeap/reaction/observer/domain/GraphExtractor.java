package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class GraphExtractor {

    public Graph getSystemRelatedGraph(Graph graph, String systemName) {
        // Filter all Reaction nodes that belong to the given system
        List<Reaction> relevantReactions = graph.nodes().stream()
                .filter(node -> node instanceof Reaction reaction && reaction.system().equals(systemName))
                .map(node -> (Reaction) node)
                .toList();

        // Collect all Trigger and Action edges related to those Reactions
        List<Edge> relevantEdges = graph.edges().stream()
                .filter(edge -> {
                    if (edge instanceof Trigger trigger) {
                        return relevantReactions.contains(trigger.target());
                    } else if (edge instanceof Action action) {
                        return relevantReactions.contains(action.source());
                    }
                    return false;
                })
                .toList();

        // Extract all Interface nodes connected via those edges
        Set<Node> relevantMessages = relevantEdges.stream()
                .flatMap(edge -> {
                    if (edge instanceof Trigger trigger) {
                        return Stream.of(trigger.source());
                    } else if (edge instanceof Action action) {
                        return Stream.of(action.target());
                    }
                    return Stream.empty();
                })
                .collect(Collectors.toSet());

        // Combine Reactions and Messages into the final node set
        Set<Node> relevantNodes = new HashSet<>(relevantReactions);
        relevantNodes.addAll(relevantMessages);

        // Return the filtered subgraph
        return new Graph(List.copyOf(relevantNodes), relevantEdges);
    }
}
