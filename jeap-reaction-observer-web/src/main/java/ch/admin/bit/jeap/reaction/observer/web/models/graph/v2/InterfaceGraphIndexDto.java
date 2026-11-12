package ch.admin.bit.jeap.reaction.observer.web.models.graph.v2;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Which interfaces have a reaction graph, and the entity tag of each.
 * <p>
 * One entry per interface of any kind. A message type with variants has one interface, and so one entry and one
 * graph, per variant.
 */
@Schema(description = "Which interfaces have a reaction graph, and their entity tags")
public record InterfaceGraphIndexDto(List<InterfaceGraphIndexEntryDto> entries) {

    /**
     * One interface.
     *
     * @param id            the id of the interface node
     * @param interfaceType the type of the interface
     * @param fqn           the fully qualified name of the interface
     * @param etag          the entity tag of the graph resource, the same string it answers with
     * @param path          where the graph of the interface is served; a consumer takes the path from here rather
     *                      than building it
     */
    @Schema(description = "One interface, with the entity tag and the path of its graph")
    public record InterfaceGraphIndexEntryDto(String id, String interfaceType, String fqn, String etag,
                                              String path) {
    }
}
