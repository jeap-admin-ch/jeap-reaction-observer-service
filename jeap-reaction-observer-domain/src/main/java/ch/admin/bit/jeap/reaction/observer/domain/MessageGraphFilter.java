package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The view of a graph as model version 1 of the reaction observer library observes it: messages only.
 * <p>
 * This is what the API v1 serves, so that its consumers see nothing they do not know while libraries start to
 * report other kinds of interfaces:
 * <ul>
 *     <li>interface nodes other than {@link Message}s are removed, and with them their trigger and action edges,</li>
 *     <li>a reaction that is left without any message (e.g. a reaction only triggered by an HTTP request) is
 *     removed,</li>
 *     <li>a reaction triggered by something else than a message keeps its message actions, without a trigger.</li>
 * </ul>
 * A reaction that never had an edge is kept, as it was before: only what is removed here can make a reaction
 * disappear. A graph of messages only is returned as it is.
 */
public final class MessageGraphFilter {

    private MessageGraphFilter() {
    }

    public static Graph filter(Graph graph) {
        // Checked on the nodes, not only on the edges below: an Interface node without any edge at all (were one
        // ever created, e.g. by a future caller) would otherwise slip through this fast path unfiltered, and
        // crash two layers downstream instead of being removed here as every other non-message interface is.
        boolean hasNonMessageInterface = graph.nodes().stream()
                .anyMatch(node -> node instanceof Interface && !(node instanceof Message));
        if (!hasNonMessageInterface) {
            return graph;
        }

        Set<Reaction> withMessage = new HashSet<>();
        Set<Reaction> withOtherInterface = new HashSet<>();
        for (Edge edge : graph.edges()) {
            Reaction reaction = reactionOf(edge);
            if (reaction == null) {
                continue;
            }
            if (interfaceOf(edge) instanceof Message) {
                withMessage.add(reaction);
            } else {
                withOtherInterface.add(reaction);
            }
        }

        Set<Reaction> removedReactions = new HashSet<>(withOtherInterface);
        removedReactions.removeAll(withMessage);

        List<Node> nodes = graph.nodes().stream()
                .filter(node -> node instanceof Reaction reaction
                        ? !removedReactions.contains(reaction)
                        : !(node instanceof Interface) || node instanceof Message)
                .toList();
        List<Edge> edges = graph.edges().stream()
                .filter(edge -> reactionOf(edge) == null || interfaceOf(edge) instanceof Message)
                .toList();
        return new Graph(nodes, edges);
    }

    private static Reaction reactionOf(Edge edge) {
        if (edge instanceof Trigger trigger) {
            return trigger.target();
        }
        if (edge instanceof Action action) {
            return action.source();
        }
        return null;
    }

    private static Interface interfaceOf(Edge edge) {
        return edge instanceof Trigger trigger ? trigger.source() : ((Action) edge).target();
    }
}
