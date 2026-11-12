package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshotV2;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshot;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphIndexDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.InterfaceGraphIndexDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * The replication indexes of the API v2: which reaction graphs exist, with the entity tag and the path of each.
 * <p>
 * As in the API v1 ({@link GraphIndexController}), nothing is built per request: the payloads and their tags come
 * from the snapshot built when the graph was last refreshed. The index of the interfaces is what a consumer takes
 * the path of an interface graph from.
 */
@RestController
@RequestMapping("/api/v2/graphs")
@RequiredArgsConstructor
@SecurityRequirement(name = "OIDC")
public class GraphIndexV2Controller {

    private final GraphHolder graphHolder;
    private final EtagSupport etagSupport;

    @PreAuthorize("hasRole('reactions', 'read')")
    @Operation(summary = "Which systems have a reaction graph, with their entity tags",
            description = "Only systems whose graph is not empty are listed.")
    @ApiResponse(responseCode = "200", description = "The index",
            content = @Content(schema = @Schema(implementation = GraphIndexDto.class)))
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping(value = "/systems", produces = MediaType.APPLICATION_JSON_VALUE)
    public @Nullable ResponseEntity<byte[]> getSystemGraphIndex(WebRequest request) {
        return etagSupport.respondWithIndex(request, snapshot().systemIndex());
    }

    @PreAuthorize("hasRole('reactions', 'read')")
    @Operation(summary = "Which components have a reaction graph, with their entity tags",
            description = "Each entry also names the system the component's reactions were published under. "
                          + "Only components whose graph is not empty are listed.")
    @ApiResponse(responseCode = "200", description = "The index",
            content = @Content(schema = @Schema(implementation = GraphIndexDto.class)))
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping(value = "/components", produces = MediaType.APPLICATION_JSON_VALUE)
    public @Nullable ResponseEntity<byte[]> getComponentGraphIndex(WebRequest request) {
        return etagSupport.respondWithIndex(request, snapshot().componentIndex());
    }

    @PreAuthorize("hasRole('reactions', 'read')")
    @Operation(summary = "Which interfaces have a reaction graph, with their entity tags and paths",
            description = "One entry per interface of any type: messages (one per variant), HTTP operations and "
                          + "every other type. The graph of an interface is fetched with the path of its entry.")
    @ApiResponse(responseCode = "200", description = "The index",
            content = @Content(schema = @Schema(implementation = InterfaceGraphIndexDto.class)))
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping(value = "/interfaces", produces = MediaType.APPLICATION_JSON_VALUE)
    public @Nullable ResponseEntity<byte[]> getInterfaceGraphIndex(
            @Parameter(description = "Only the interfaces of this type, e.g. event, command or http_request; "
                                     + "matched exactly. All interfaces when absent.", example = "http_request")
            @RequestParam(required = false) @Nullable String interfaceType, WebRequest request) {
        return etagSupport.respondWithIndex(request, snapshot().interfaceIndex(interfaceType));
    }

    /** The indexes this instance answers from - see {@link GraphSnapshot#required}. */
    private GraphSnapshotV2 snapshot() {
        return GraphSnapshot.required(graphHolder.getSnapshot()).v2();
    }
}
