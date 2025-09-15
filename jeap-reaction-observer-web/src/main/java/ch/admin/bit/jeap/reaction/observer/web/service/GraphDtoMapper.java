package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.*;

import java.util.List;

public class GraphDtoMapper {

    public static GraphDto map(Graph domainGraph) {
        List<NodeDto> nodes = domainGraph.nodes().stream()
                .map(node -> {
                    if (node instanceof Message message) return MessageNodeDto.from(message);
                    if (node instanceof Reaction reaction) return ReactionNodeDto.from(reaction);
                    throw new IllegalArgumentException("Unknown node type: " + node.getClass());
                })
                .map(NodeDto.class::cast)
                .toList();

        List<EdgeDto> edges = domainGraph.edges().stream()
                .map(edge -> {
                    if (edge instanceof Trigger trigger) return TriggerEdgeDto.from(trigger);
                    if (edge instanceof Action action) return ActionEdgeDto.from(action);
                    throw new IllegalArgumentException("Unknown edge type: " + edge.getClass());
                })
                .map(EdgeDto.class::cast)
                .toList();

        return new GraphDto(nodes, edges);
    }
}
