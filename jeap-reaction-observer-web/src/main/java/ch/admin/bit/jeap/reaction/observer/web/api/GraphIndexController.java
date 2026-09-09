package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshot;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphIndexDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphIndexDto.GraphIndexEntryDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.MessageGraphIndexDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.MessageGraphIndexDto.MessageGraphIndexEntryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * The replication indexes: which reaction graphs exist, and the entity tag of each.
 * <p>
 * One call per kind tells a consumer everything it needs to decide what to fetch, and a consumer that already
 * has the index gets a {@code 304}. Without them, finding out whether anything changed costs one request per
 * system, per component and per message type - and the fingerprint that would answer the question is inside
 * the payload.
 * <p>
 * The entries come from the snapshot built when the graph was last refreshed, so serving an index extracts
 * nothing.
 */
@RestController
@RequestMapping("/api/graphs")
@RequiredArgsConstructor
@Slf4j
public class GraphIndexController {

    private final GraphHolder graphHolder;
    private final EtagSupport etagSupport;

    @PreAuthorize("@reactionsApiAuthorization.canRead()")
    @Operation(summary = "Which systems have a reaction graph, with their entity tags",
            description = "One call tells a consumer which system graphs changed since it last replicated "
                          + "them. Only systems whose graph is not empty are listed.")
    @ApiResponse(responseCode = "200", description = "The index",
            content = @Content(schema = @Schema(implementation = GraphIndexDto.class)))
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping(value = "/systems", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> getSystemGraphIndex(WebRequest request) {
        GraphSnapshot snapshot = snapshot();
        GraphIndexDto index = new GraphIndexDto(snapshot.systems().stream()
                .map(entry -> new GraphIndexEntryDto(entry.name(), null,
                        etagSupport.entityTag(entry.fingerprint()),
                        "/api/graphs/systems/" + encode(entry.name())))
                .toList());
        return etagSupport.respondSerialized(request, index);
    }

    @PreAuthorize("@reactionsApiAuthorization.canRead()")
    @Operation(summary = "Which components have a reaction graph, with their entity tags",
            description = "Each entry also names the system the component's reactions were published under, "
                          + "so a consumer does not have to guess it. Only components whose graph is not "
                          + "empty are listed.")
    @ApiResponse(responseCode = "200", description = "The index",
            content = @Content(schema = @Schema(implementation = GraphIndexDto.class)))
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping(value = "/components", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> getComponentGraphIndex(WebRequest request) {
        GraphSnapshot snapshot = snapshot();
        GraphIndexDto index = new GraphIndexDto(snapshot.components().stream()
                .map(entry -> new GraphIndexEntryDto(entry.name(), entry.system(),
                        etagSupport.entityTag(entry.fingerprint()),
                        "/api/graphs/components/" + encode(entry.name())))
                .toList());
        return etagSupport.respondSerialized(request, index);
    }

    @PreAuthorize("@reactionsApiAuthorization.canRead()")
    @Operation(summary = "Which message types have reaction graphs, with their variants and entity tags",
            description = "The variants are the keys the graph resource answers with, and the entity tag "
                          + "covers all of them.")
    @ApiResponse(responseCode = "200", description = "The index",
            content = @Content(schema = @Schema(implementation = MessageGraphIndexDto.class)))
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping(value = "/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> getMessageGraphIndex(WebRequest request) {
        GraphSnapshot snapshot = snapshot();
        MessageGraphIndexDto index = new MessageGraphIndexDto(snapshot.messageTypes().stream()
                .map(entry -> new MessageGraphIndexEntryDto(entry.messageType(), entry.variantKeys(),
                        etagSupport.entityTag(entry.fingerprint()),
                        "/api/graphs/messages/" + encode(entry.messageType())))
                .toList());
        return etagSupport.respondSerialized(request, index);
    }

    /**
     * A name as a path segment. Component names are service names and message types are identifiers, so this
     * changes nothing today - and it is what keeps the path usable if one of them ever carries a character
     * that has to be escaped.
     */
    private static String encode(String name) {
        return UriUtils.encodePathSegment(name, StandardCharsets.UTF_8);
    }

    /**
     * The snapshot to answer from, never null: a holder that has never been given a graph answers as an empty
     * landscape would rather than failing the request.
     */
    private GraphSnapshot snapshot() {
        GraphSnapshot snapshot = graphHolder.getSnapshot();
        return snapshot == null ? GraphSnapshot.empty() : snapshot;
    }
}
