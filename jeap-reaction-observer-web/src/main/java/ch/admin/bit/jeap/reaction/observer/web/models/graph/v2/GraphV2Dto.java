package ch.admin.bit.jeap.reaction.observer.web.models.graph.v2;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A graph of the API v2: reactions and interfaces of every kind, and the edges between them.
 *
 * @param nodes described in the OpenAPI document as a list of {@link NodeV2Dto} and not as a choice of the kinds
 *              of node known today, which a node of a kind added later would not be one of
 */
public record GraphV2Dto(
        @ArraySchema(schema = @Schema(implementation = NodeV2Dto.class))
        List<NodeV2Dto> nodes,
        List<EdgeV2Dto> edges
) {
}
