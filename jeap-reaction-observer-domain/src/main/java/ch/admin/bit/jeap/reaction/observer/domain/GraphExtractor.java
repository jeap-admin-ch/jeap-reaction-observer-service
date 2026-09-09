package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class GraphExtractor {

    public Graph getSystemRelatedGraph(Graph graph, String systemName) {
        return getFilteredGraph(graph, reaction ->
                reaction.system() != null && reaction.system().equalsIgnoreCase(systemName)
        );
    }

    public Graph getComponentRelatedGraph(Graph graph, String componentName) {
        return getFilteredGraph(graph, reaction -> reaction.component().equals(componentName));
    }

    public Graph getMessageRelatedGraph(Graph graph, String messageType, String variant) {
        // Find the message node matching the given type and variant
        Optional<Message> messageOpt = graph.nodes().stream()
                .filter(node -> node instanceof Message message &&
                        message.messageType().equals(messageType) &&
                        Objects.equals(message.variant(), variant))
                .map(Message.class::cast)
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
                .map(Trigger.class::cast)
                .toList();

        // Find all Action edges where the message is the target
        List<Action> incomingActions = graph.edges().stream()
                .filter(edge -> edge instanceof Action action && action.target().equals(message))
                .map(Action.class::cast)
                .toList();

        // Collect all related Reaction nodes from both directions
        Set<Node> relatedReactions = Stream.concat(
                        outgoingTriggers.stream().map(Trigger::target),
                        incomingActions.stream().map(Action::source)
                )
                .collect(Collectors.toSet());

        // Collect adjacent messages connected to those reactions
        Set<Node> adjacentMessages = getAdjacentMessagesForReactions(graph, relatedReactions);

        // Combine all relevant nodes
        Set<Node> relevantNodes = new HashSet<>();
        relevantNodes.add(message);
        relevantNodes.addAll(relatedReactions);
        relevantNodes.addAll(adjacentMessages);

        // Collect all edges between relevant nodes
        List<Edge> relevantEdges = graph.edges().stream()
                .filter(edge -> {
                    if (edge instanceof Trigger trigger) {
                        return relevantNodes.contains(trigger.source()) && relevantNodes.contains(trigger.target());
                    } else if (edge instanceof Action action) {
                        return relevantNodes.contains(action.source()) && relevantNodes.contains(action.target());
                    }
                    return false;
                })
                .toList();

        return new Graph(List.copyOf(relevantNodes), relevantEdges);
    }

    /**
     * Finds all messages that are connected to the given reactions.
     * This includes:
     * - Messages that triggered the reaction (via Trigger.source)
     * - Messages that were produced by the reaction (via Action.target)
     */
    private Set<Node> getAdjacentMessagesForReactions(Graph graph, Set<Node> reactions) {
        return reactions.stream()
                .flatMap(reactionNode -> {
                    Reaction reaction = (Reaction) reactionNode;

                    // Find messages that triggered this reaction
                    Stream<Node> triggerSources = graph.edges().stream()
                            .filter(edge -> edge instanceof Trigger trigger && trigger.target().equals(reaction))
                            .map(edge -> ((Trigger) edge).source());

                    // Find messages that were produced by this reaction
                    Stream<Node> actionTargets = graph.edges().stream()
                            .filter(edge -> edge instanceof Action action && action.source().equals(reaction))
                            .map(edge -> ((Action) edge).target());

                    return Stream.concat(triggerSources, actionTargets);
                })
                .collect(Collectors.toSet());
    }

    /**
     * The subgraph of <b>every</b> system in one pass, keyed by the lower-cased system name.
     * <p>
     * Equivalent to calling {@link #getSystemRelatedGraph} for each system, and that equivalence is what
     * {@code GraphExtractorTest} asserts - but at the cost of one pass over the nodes and one over the edges
     * instead of one of each per system. It is what makes fingerprinting every subgraph of a landscape on
     * every refresh affordable.
     * <p>
     * <b>Lower-cased keys</b>, because {@link #getSystemRelatedGraph} matches a system ignoring case: two
     * spellings of one system are one subgraph there and have to be one entry here.
     */
    public Map<String, Graph> getSystemRelatedGraphs(Graph graph) {
        return getFilteredGraphs(graph, reaction -> reaction.system() == null
                ? null
                : reaction.system().toLowerCase(Locale.ROOT));
    }

    /**
     * The subgraph of <b>every</b> component in one pass, keyed by the component name.
     * <p>
     * The counterpart of {@link #getSystemRelatedGraphs}; the key is exact, because
     * {@link #getComponentRelatedGraph} matches a component name exactly.
     */
    public Map<String, Graph> getComponentRelatedGraphs(Graph graph) {
        return getFilteredGraphs(graph, Reaction::component);
    }

    /**
     * The subgraph of <b>every</b> message node in one pass, keyed by the message type and its variant.
     * <p>
     * Equivalent to calling {@link #getMessageRelatedGraph} for each of them, again asserted by the tests. The
     * neighbourhood of one message is small; what this avoids is walking every edge of the graph four times
     * for each of them.
     */
    public Map<MessageKey, Graph> getMessageRelatedGraphs(Graph graph) {
        Map<Message, List<Trigger>> triggersBySource = new HashMap<>();
        Map<Reaction, List<Trigger>> triggersByTarget = new HashMap<>();
        Map<Message, List<Action>> actionsByTarget = new HashMap<>();
        Map<Reaction, List<Action>> actionsBySource = new HashMap<>();
        for (Edge edge : graph.edges()) {
            if (edge instanceof Trigger trigger && trigger.source() instanceof Message message) {
                triggersBySource.computeIfAbsent(message, key -> new ArrayList<>()).add(trigger);
                triggersByTarget.computeIfAbsent(trigger.target(), key -> new ArrayList<>()).add(trigger);
            } else if (edge instanceof Action action && action.target() instanceof Message message) {
                actionsByTarget.computeIfAbsent(message, key -> new ArrayList<>()).add(action);
                actionsBySource.computeIfAbsent(action.source(), key -> new ArrayList<>()).add(action);
            }
        }

        Map<MessageKey, Graph> graphs = new LinkedHashMap<>();
        for (Node node : graph.nodes()) {
            if (node instanceof Message message) {
                MessageKey key = new MessageKey(message.messageType(), message.variant());
                // computeIfAbsent, so that the first node of a key wins - as findFirst() does in
                // getMessageRelatedGraph
                graphs.computeIfAbsent(key, ignored -> messageRelatedGraph(message, triggersBySource,
                        triggersByTarget, actionsByTarget, actionsBySource));
            }
        }
        return graphs;
    }

    private Graph messageRelatedGraph(Message message,
                                      Map<Message, List<Trigger>> triggersBySource,
                                      Map<Reaction, List<Trigger>> triggersByTarget,
                                      Map<Message, List<Action>> actionsByTarget,
                                      Map<Reaction, List<Action>> actionsBySource) {
        List<Trigger> outgoingTriggers = triggersBySource.getOrDefault(message, List.of());
        List<Action> incomingActions = actionsByTarget.getOrDefault(message, List.of());

        Set<Node> relatedReactions = Stream.concat(
                        outgoingTriggers.stream().map(Trigger::target),
                        incomingActions.stream().map(Action::source))
                .collect(Collectors.toSet());

        Set<Node> relevantNodes = new HashSet<>();
        relevantNodes.add(message);
        relevantNodes.addAll(relatedReactions);
        relatedReactions.forEach(reactionNode -> {
            Reaction reaction = (Reaction) reactionNode;
            triggersByTarget.getOrDefault(reaction, List.of())
                    .forEach(trigger -> relevantNodes.add(trigger.source()));
            actionsBySource.getOrDefault(reaction, List.of())
                    .forEach(action -> relevantNodes.add(action.target()));
        });

        // Every edge between two relevant nodes, in the graph's own order - the same set getMessageRelatedGraph
        // arrives at, reached from the ends rather than by scanning the whole graph
        List<Edge> relevantEdges = Stream.concat(
                        relevantNodes.stream()
                                .filter(Message.class::isInstance)
                                .flatMap(node -> triggersBySource.getOrDefault((Message) node, List.of())
                                        .stream()),
                        relevantNodes.stream()
                                .filter(Reaction.class::isInstance)
                                .flatMap(node -> actionsBySource.getOrDefault((Reaction) node, List.of())
                                        .stream()))
                .filter(edge -> edge instanceof Trigger trigger
                        ? relevantNodes.contains(trigger.source()) && relevantNodes.contains(trigger.target())
                        : relevantNodes.contains(((Action) edge).source())
                          && relevantNodes.contains(((Action) edge).target()))
                .map(Edge.class::cast)
                .toList();

        return new Graph(List.copyOf(relevantNodes), relevantEdges);
    }

    /**
     * One filtered subgraph per key a reaction belongs to. A reaction whose key is null belongs to none - the
     * way a reaction without a system is in no system's subgraph.
     */
    private Map<String, Graph> getFilteredGraphs(Graph graph, Function<Reaction, String> keyOf) {
        Map<String, List<Reaction>> reactionsByKey = new LinkedHashMap<>();
        for (Node node : graph.nodes()) {
            if (node instanceof Reaction reaction) {
                String key = keyOf.apply(reaction);
                if (key != null) {
                    reactionsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(reaction);
                }
            }
        }

        Map<String, List<Edge>> edgesByKey = new LinkedHashMap<>();
        Map<Reaction, List<String>> keysByReaction = new HashMap<>();
        reactionsByKey.forEach((key, reactions) ->
                reactions.forEach(reaction ->
                        keysByReaction.computeIfAbsent(reaction, r -> new ArrayList<>()).add(key)));
        for (Edge edge : graph.edges()) {
            Reaction reaction = reactionOf(edge);
            if (reaction == null) {
                continue;
            }
            keysByReaction.getOrDefault(reaction, List.of())
                    .forEach(key -> edgesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(edge));
        }

        Map<String, Graph> graphs = new LinkedHashMap<>();
        reactionsByKey.forEach((key, reactions) -> {
            List<Edge> edges = edgesByKey.getOrDefault(key, List.of());
            Set<Node> nodes = new HashSet<>(reactions);
            edges.forEach(edge -> nodes.add(messageOf(edge)));
            graphs.put(key, new Graph(List.copyOf(nodes), List.copyOf(edges)));
        });
        return graphs;
    }

    /** The reaction an edge belongs to: a trigger's target, an action's source. */
    private static Reaction reactionOf(Edge edge) {
        if (edge instanceof Trigger trigger) {
            return trigger.target();
        }
        if (edge instanceof Action action) {
            return action.source();
        }
        return null;
    }

    /** The message at the other end of an edge from its reaction. */
    private static Node messageOf(Edge edge) {
        return edge instanceof Trigger trigger ? trigger.source() : ((Action) edge).target();
    }

    /** A message node's identity in {@link #getMessageRelatedGraphs}: its type and its variant, which may be null. */
    public record MessageKey(String messageType, String variant) {
    }

    public Graph getFilteredGraph(Graph graph, Predicate<Reaction> reactionFilter) {
        // Filter all Reaction nodes based on the given predicate
        List<Reaction> relevantReactions = graph.nodes().stream()
                .filter(node -> node instanceof Reaction reaction && reactionFilter.test(reaction))
                .map(Reaction.class::cast)
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
