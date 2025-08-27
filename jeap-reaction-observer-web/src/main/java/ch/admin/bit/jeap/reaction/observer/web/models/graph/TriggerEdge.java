package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TriggerEdge(
        @JsonProperty("edgeType") EdgeType edgeType,
        long sourceId,
        NodeType sourceNodeType,
        long targetReactionId,
        Integer median
) implements Edge {
}
