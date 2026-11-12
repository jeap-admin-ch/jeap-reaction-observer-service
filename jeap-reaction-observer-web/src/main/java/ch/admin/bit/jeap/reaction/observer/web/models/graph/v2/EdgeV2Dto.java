package ch.admin.bit.jeap.reaction.observer.web.models.graph.v2;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Action;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Trigger;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An edge of a graph of the API v2: its type and the ids of its two nodes, nothing else. Which kind of node is at
 * which end follows from the type, and the median of a reaction is on its node.
 */
@Schema(description = "An edge of the graph, from the node sourceId to the node targetId")
public record EdgeV2Dto(
        @Schema(description = "Kind of edge. Known values: TRIGGER (interface to reaction), ACTION (reaction to "
                              + "interface), PREDECESSOR (reaction to a reaction that may be its predecessor; only "
                              + "in a response that has both reactions). Not a closed set: ignore an edge with an "
                              + "unknown value.",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "TRIGGER")
        String edgeType,
        @Schema(description = "Id of the node the edge starts at", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "I14")
        String sourceId,
        @Schema(description = "Id of the node the edge ends at", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "R201")
        String targetId
) {

    public static final String TRIGGER = "TRIGGER";
    public static final String ACTION = "ACTION";
    /** Defined by the contract; the service does not create edges of this type yet. */
    public static final String PREDECESSOR = "PREDECESSOR";

    public static EdgeV2Dto from(Trigger trigger) {
        return new EdgeV2Dto(TRIGGER, NodeV2Id.ofInterface(trigger.source().id()),
                NodeV2Id.ofReaction(trigger.target().id()));
    }

    public static EdgeV2Dto from(Action action) {
        return new EdgeV2Dto(ACTION, NodeV2Id.ofReaction(action.source().id()),
                NodeV2Id.ofInterface(action.target().id()));
    }
}
