package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class GraphExtractor {

    public Graph getSystemRelatedGraph(Graph graph, String systemName) {
        return getFilteredGraph(graph, reaction ->
                Objects.equals(reaction.system(), systemName)
        );
    }

    public Graph getComponentRelatedGraph(Graph graph, String componentName) {
        return getFilteredGraph(graph, reaction -> reaction.component().equals(componentName));
    }

    public Graph getMessageRelatedGraph(Graph graph, String messageType, String variant) {
        // Find the Message node with the given type and (possibly null) variant
        Optional<Message> messageOpt = graph.nodes().stream()
                .filter(node -> node instanceof Message message
                        && message.messageType().equals(messageType)
                        && Objects.equals(message.variant(), variant))
                .map(node -> (Message) node)
                .findFirst();

        if (messageOpt.isEmpty()) {
            log.warn("No matching message found for type='{}' and variant='{}'. Returning empty graph.",
                    messageType, variant);
            return new Graph(List.of(), List.of());
        }

        Message message = messageOpt.get();

        // Find all Trigger edges where the message is the source
        List<Trigger> outgoingTriggers = graph.edges().stream()
                .filter(edge -> edge instanceof Trigger trigger && trigger.source().equals(message))
                .map(edge -> (Trigger) edge)
                .toList();

        // Find all Action edges where the message is the target
        List<Action> incomingActions = graph.edges().stream()
                .filter(edge -> edge instanceof Action action && action.target().equals(message))
                .map(edge -> (Action) edge)
                .toList();

        // Collect all related Reaction nodes from both directions
        Set<Node> relatedReactions = Stream.concat(
                        outgoingTriggers.stream().map(Trigger::target),
                        incomingActions.stream().map(Action::source)
                )
                .collect(Collectors.toSet());

        // Combine the message and related reactions into the final node set
        Set<Node> relevantNodes = new HashSet<>();
        relevantNodes.add(message);
        relevantNodes.addAll(relatedReactions);

        // Combine all relevant edges
        List<Edge> relevantEdges = new ArrayList<>();
        relevantEdges.addAll(outgoingTriggers);
        relevantEdges.addAll(incomingActions);

        return new Graph(List.copyOf(relevantNodes), relevantEdges);
    }

    public Graph getFilteredGraph(Graph graph, Predicate<Reaction> reactionFilter) {
        // Filter all Reaction nodes based on the given predicate
        List<Reaction> relevantReactions = graph.nodes().stream()
                .filter(node -> node instanceof Reaction reaction && reactionFilter.test(reaction))
                .map(node -> (Reaction) node)
                .toList();

        if (relevantReactions.isEmpty()) {
            log.warn("No reactions found for filter. Returning empty graph.");
            return new Graph(List.of(), List.of());
        }

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
