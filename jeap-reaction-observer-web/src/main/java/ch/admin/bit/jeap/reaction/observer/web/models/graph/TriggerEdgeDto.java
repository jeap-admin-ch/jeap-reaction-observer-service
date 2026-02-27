package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Trigger;

public record TriggerEdgeDto(
        long sourceId,
        NodeDtoType sourceNodeType,
        long targetReactionId,
        Integer median
) implements EdgeDto {
    public static TriggerEdgeDto from(Trigger trigger) {
        return new TriggerEdgeDto(
                trigger.source().getId(),
                //Currently it can only be a MessageNode
                NodeDtoType.MESSAGE,
                trigger.target().id(),
                trigger.median()
        );
    }

    @Override
    public String getCanonicalId() {
        return EdgeDtoType.TRIGGER + "-" + sourceId + "-" + targetReactionId;
    }
}
