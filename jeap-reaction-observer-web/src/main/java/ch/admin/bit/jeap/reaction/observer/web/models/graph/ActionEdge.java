package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActionEdge(
        @JsonProperty("edgeType") EdgeType edgeType,
        long sourceReactionId,
        long targetId,
        NodeType targetNodeType
) implements Edge {
}
