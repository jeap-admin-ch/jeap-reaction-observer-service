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
     * <p>
     * Meant for a graph of messages only, which is what the API v1 serves (see {@link MessageGraphFilter}).
     */
    public Map<MessageKey, Graph> getMessageRelatedGraphs(Graph graph) {
        EdgeIndex index = EdgeIndex.of(graph);
        Map<MessageKey, Graph> graphs = new LinkedHashMap<>();
        for (Node node : graph.nodes()) {
            if (node instanceof Message message) {
                MessageKey key = new MessageKey(message.messageType(), message.variant());
                // computeIfAbsent, so that the first node of a key wins - as findFirst() does in
                // getMessageRelatedGraph
                graphs.computeIfAbsent(key, ignored -> interfaceRelatedGraph(message, index));
            }
        }
        return graphs;
    }

    /**
     * The subgraph of one interface of any kind: every reaction that has the interface as its trigger or as one of
     * its actions, <b>whole</b> - with its trigger and all its actions, whatever kind of interface they are.
     * <p>
     * One level, as the message graphs are: a reaction that is only connected to one of the neighbouring interfaces
     * is not part of it.
     *
     * @return the subgraph, or an empty graph when the graph has no interface with this id
     */
    public Graph getInterfaceRelatedGraph(Graph graph, long interfaceId) {
        return graph.nodes().stream()
                .filter(node -> node instanceof Interface && node.id() == interfaceId)
                .map(Interface.class::cast)
                .findFirst()
                .map(iface -> interfaceRelatedGraph(iface, EdgeIndex.of(graph)))
                .orElseGet(() -> new Graph(List.of(), List.of()));
    }

    /**
     * The subgraph of <b>every</b> interface in one pass, keyed by the id of the interface.
     * <p>
     * Equivalent to calling {@link #getInterfaceRelatedGraph} for each of them, asserted by the tests.
     */
    public Map<Long, Graph> getInterfaceRelatedGraphs(Graph graph) {
        EdgeIndex index = EdgeIndex.of(graph);
        Map<Long, Graph> graphs = new LinkedHashMap<>();
        for (Node node : graph.nodes()) {
            if (node instanceof Interface iface) {
                graphs.computeIfAbsent(iface.id(), ignored -> interfaceRelatedGraph(iface, index));
            }
        }
        return graphs;
    }

    private static Graph interfaceRelatedGraph(Interface iface, EdgeIndex index) {
        Set<Reaction> relatedReactions = new LinkedHashSet<>();
        index.triggersBySource().getOrDefault(iface, List.of())
                .forEach(trigger -> relatedReactions.add(trigger.target()));
        index.actionsByTarget().getOrDefault(iface, List.of())
                .forEach(action -> relatedReactions.add(action.source()));

        // The related reactions with every edge of theirs, and the interfaces at the other ends
        Set<Node> relevantNodes = new LinkedHashSet<>();
        relevantNodes.add(iface);
        relevantNodes.addAll(relatedReactions);
        List<Edge> relevantEdges = new ArrayList<>();
        for (Reaction reaction : relatedReactions) {
            for (Trigger trigger : index.triggersByTarget().getOrDefault(reaction, List.of())) {
                relevantNodes.add(trigger.source());
                relevantEdges.add(trigger);
            }
            for (Action action : index.actionsBySource().getOrDefault(reaction, List.of())) {
                relevantNodes.add(action.target());
                relevantEdges.add(action);
            }
        }
        return new Graph(List.copyOf(relevantNodes), List.copyOf(relevantEdges));
    }

    /** The edges of a graph by each of their ends, so that a neighbourhood is looked up rather than searched. */
    private record EdgeIndex(Map<Interface, List<Trigger>> triggersBySource,
                             Map<Reaction, List<Trigger>> triggersByTarget,
                             Map<Interface, List<Action>> actionsByTarget,
                             Map<Reaction, List<Action>> actionsBySource) {

        static EdgeIndex of(Graph graph) {
            EdgeIndex index = new EdgeIndex(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
            for (Edge edge : graph.edges()) {
                if (edge instanceof Trigger trigger) {
                    index.triggersBySource.computeIfAbsent(trigger.source(), key -> new ArrayList<>()).add(trigger);
                    index.triggersByTarget.computeIfAbsent(trigger.target(), key -> new ArrayList<>()).add(trigger);
                } else if (edge instanceof Action action) {
                    index.actionsByTarget.computeIfAbsent(action.target(), key -> new ArrayList<>()).add(action);
                    index.actionsBySource.computeIfAbsent(action.source(), key -> new ArrayList<>()).add(action);
                }
            }
            return index;
        }
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
