package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Action;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ActionEdgeDto(
        long sourceReactionId,
        long targetId,
        NodeDtoType targetNodeType
) implements EdgeDto {
    public static ActionEdgeDto from(Action action) {
        return new ActionEdgeDto(
                action.source().id(),
                action.target().getId(),
                //Currently it can only be a MessageNode
                NodeDtoType.MESSAGE
        );
    }
}
