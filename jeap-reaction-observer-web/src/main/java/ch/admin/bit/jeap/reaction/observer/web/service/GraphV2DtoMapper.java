package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Action;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Edge;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.HttpOperation;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Interface;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Node;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Trigger;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.EdgeV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.GraphV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.HttpOperationNodeV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.InterfaceNodeV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.MessageNodeV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.NodeV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.ReactionNodeV2Dto;

import java.util.List;

/**
 * Maps the graph of the domain to the API v2, with every kind of interface.
 */
public final class GraphV2DtoMapper {

    private GraphV2DtoMapper() {
    }

    public static GraphV2Dto map(Graph domainGraph) {
        List<NodeV2Dto> nodes = domainGraph.nodes().stream()
                .map(GraphV2DtoMapper::map)
                .toList();
        // An edge of the v2 is its type and its two ends only, so two edges that are equal are one
        List<EdgeV2Dto> edges = domainGraph.edges().stream()
                .map(GraphV2DtoMapper::map)
                .distinct()
                .toList();
        return new GraphV2Dto(nodes, edges);
    }

    /**
     * A node of the kind of its domain node: typed for the kinds of interface the service presents in their own
     * way, the generic interface node for every other.
     */
    private static NodeV2Dto map(Node node) {
        if (node instanceof Reaction reaction) {
            return ReactionNodeV2Dto.from(reaction);
        }
        if (node instanceof Message message) {
            return MessageNodeV2Dto.from(message);
        }
        if (node instanceof HttpOperation operation) {
            return HttpOperationNodeV2Dto.from(operation);
        }
        if (node instanceof Interface iface) {
            return InterfaceNodeV2Dto.from(iface);
        }
        throw new IllegalArgumentException("Unknown node type: " + node.getClass());
    }

    private static EdgeV2Dto map(Edge edge) {
        if (edge instanceof Trigger trigger) {
            return EdgeV2Dto.from(trigger);
        }
        if (edge instanceof Action action) {
            return EdgeV2Dto.from(action);
        }
        throw new IllegalArgumentException("Unknown edge type: " + edge.getClass());
    }
}
