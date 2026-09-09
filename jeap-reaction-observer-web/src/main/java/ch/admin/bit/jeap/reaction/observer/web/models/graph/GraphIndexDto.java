package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Which graphs of one kind exist, and the entity tag of each.
 * <p>
 * The point of an index is that a consumer learns in one call which graphs it has to fetch. It lists what is
 * <em>available</em> rather than what changed, because otherwise "unchanged" and "gone" would be
 * indistinguishable and nothing could ever be pruned.
 */
@Schema(description = "Which reaction graphs exist, and their entity tags")
public record GraphIndexDto(List<GraphIndexEntryDto> entries) {

    /**
     * One graph.
     *
     * @param name   the system or component the graph is of
     * @param system the system a component's reactions were published under; null in the index of systems
     * @param etag   the entity tag of the graph resource, the same string it answers with
     * @param path   where the graph is served, so a consumer does not have to build the URL from a name
     */
    @Schema(description = "One reaction graph, with the entity tag of its content")
    public record GraphIndexEntryDto(String name, String system, String etag, String path) {
    }
}
