package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Which message types have a reaction graph, with their variants and one entity tag each.
 * <p>
 * <b>One entry per message type, not per variant</b>, because that is the granularity the graph resource
 * answers at: {@code /api/graphs/messages/{messageType}} returns every variant of the type at once. The
 * variants are listed so a consumer knows which of them exist - and its tag covers all of them, so a variant
 * appearing or disappearing moves it.
 */
@Schema(description = "Which message types have reaction graphs, and their entity tags")
public record MessageGraphIndexDto(List<MessageGraphIndexEntryDto> entries) {

    /**
     * One message type.
     *
     * @param messageType the message type
     * @param variants    the keys the graph resource answers with - the message type alone when it has no
     *                    variant, and {@code messageType/variant} otherwise
     * @param etag        the entity tag of the graph resource, the same string it answers with
     * @param path        where the graphs of this message type are served
     */
    @Schema(description = "One message type, with its variants and the entity tag of its graphs")
    public record MessageGraphIndexEntryDto(String messageType, List<String> variants, String etag,
                                            String path) {
    }
}
