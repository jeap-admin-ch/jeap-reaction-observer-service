package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshot.IndexPayload;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphIndexDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.MessageGraphIndexDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.nio.charset.StandardCharsets;

/**
 * The replication indexes: which reaction graphs exist, and the entity tag of each.
 * <p>
 * One call per kind tells a consumer everything it needs to decide what to fetch, and a consumer that already
 * has the index gets a {@code 304}. Without them, finding out whether anything changed costs one request per
 * system, per component and per message type - and the fingerprint that would answer the question is inside
 * the payload.
 * <p>
 * <b>Nothing is built per request.</b> The payloads and their tags come from the snapshot built when the graph
 * was last refreshed, so serving an index writes bytes that already exist and a {@code 304} writes none.
 */
@RestController
@RequestMapping("/api/graphs")
@RequiredArgsConstructor
@Slf4j
public class GraphIndexController {

    /** What an index is before there is a snapshot to serve one from. */
    private static final byte[] EMPTY_INDEX = "{\"entries\":[]}".getBytes(StandardCharsets.UTF_8);

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
    public @Nullable ResponseEntity<byte[]> getSystemGraphIndex(WebRequest request) {
        return respond(request, graphHolder.getSnapshot().systemIndex());
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
    public @Nullable ResponseEntity<byte[]> getComponentGraphIndex(WebRequest request) {
        return respond(request, graphHolder.getSnapshot().componentIndex());
    }

    @PreAuthorize("@reactionsApiAuthorization.canRead()")
    @Operation(summary = "Which message types have reaction graphs, with their variants and entity tags",
            description = "The variants are the keys the graph resource answers with, and the entity tag "
                          + "covers all of them.")
    @ApiResponse(responseCode = "200", description = "The index",
            content = @Content(schema = @Schema(implementation = MessageGraphIndexDto.class)))
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping(value = "/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public @Nullable ResponseEntity<byte[]> getMessageGraphIndex(WebRequest request) {
        return respond(request, graphHolder.getSnapshot().messageIndex());
    }

    /**
     * The index as it was built, or an empty one before the first refresh - which is a landscape nothing has
     * been observed in yet, not an error.
     */
    private @Nullable ResponseEntity<byte[]> respond(WebRequest request,
                                                     @Nullable IndexPayload index) {
        if (index == null) {
            // Before the first refresh - which GraphHolder makes a transient state, not a lasting one. No
            // entity tag, so a consumer asks again rather than caching an emptiness.
            return ResponseEntity.ok(EMPTY_INDEX);
        }
        return etagSupport.respond(request, index.bytes(), index.etag());
    }
}
