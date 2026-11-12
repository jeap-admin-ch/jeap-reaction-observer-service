package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshot;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.GraphV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.GraphWithFingerprintV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.NodeV2Id;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphV2DtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.OptionalLong;

/**
 * The reaction graphs of the API v2: the whole graph, and the subgraph of one system, component or interface.
 * <p>
 * Served from the full graph, with every kind of interface - where the API v1 ({@link GraphController}) shows
 * messages only. A view always selects <b>whole</b> reactions, with their trigger and all their actions; there is
 * no filter by interface type.
 * <p>
 * Entity tags and conditional requests work as in the API v1, with fingerprints of their own: every handler reads
 * the snapshot exactly once, and a conditional request that matches is answered without extracting anything.
 */
@RestController
@RequestMapping("/api/v2/graphs")
@RequiredArgsConstructor
@SecurityRequirement(name = "OIDC")
public class GraphV2Controller {

    private final GraphHolder graphHolder;
    private final GraphFingerprintCalculator fingerprintCalculator;
    private final GraphExtractor graphExtractor;
    private final EtagSupport etagSupport;

    @PreAuthorize("hasRole('reactions', 'read')")
    @Operation(summary = "Get the graph of all reactions")
    @ApiResponse(responseCode = "200", description = "The graph")
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping
    public @Nullable ResponseEntity<GraphWithFingerprintV2Dto> getAllReactionsGraph(WebRequest request) {
        GraphSnapshot snapshot = snapshot();
        String fingerprint = snapshot.v2().graphFingerprint();
        if (etagSupport.isNotModified(request, etagSupport.entityTag(fingerprint))) {
            return null;
        }
        return answer(snapshot.fullGraph(), fingerprint);
    }

    @PreAuthorize("hasRole('reactions', 'read')")
    @Operation(summary = "Get the graph of the reactions of a system")
    @ApiResponse(responseCode = "200", description = "The graph")
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @ApiResponse(responseCode = "404", description = "No reaction of this system", content = @Content)
    @GetMapping("/systems/{systemName}")
    public @Nullable ResponseEntity<GraphWithFingerprintV2Dto> getSystemRelatedGraph(
            @PathVariable String systemName, WebRequest request) {
        GraphSnapshot snapshot = snapshot();
        String fingerprint = snapshot.v2().fingerprintOfSystem(systemName);
        if (etagSupport.isNotModified(request, etagSupport.entityTag(fingerprint))) {
            return null;
        }
        return answerOrNotFound(graphExtractor.getSystemRelatedGraph(snapshot.fullGraph(), systemName), fingerprint);
    }

    @PreAuthorize("hasRole('reactions', 'read')")
    @Operation(summary = "Get the graph of the reactions of a component")
    @ApiResponse(responseCode = "200", description = "The graph")
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @ApiResponse(responseCode = "404", description = "No reaction of this component", content = @Content)
    @GetMapping("/components/{componentName}")
    public @Nullable ResponseEntity<GraphWithFingerprintV2Dto> getComponentRelatedGraph(
            @PathVariable String componentName, WebRequest request) {
        GraphSnapshot snapshot = snapshot();
        String fingerprint = snapshot.v2().fingerprintOfComponent(componentName);
        if (etagSupport.isNotModified(request, etagSupport.entityTag(fingerprint))) {
            return null;
        }
        return answerOrNotFound(graphExtractor.getComponentRelatedGraph(snapshot.fullGraph(), componentName),
                fingerprint);
    }

    @PreAuthorize("hasRole('reactions', 'read')")
    @Operation(summary = "Get the graph of the reactions of an interface",
            description = "Every reaction that has the interface as its trigger or as one of its actions, each "
                          + "with its trigger and all its actions. Reactions that are only connected to one of "
                          + "these neighbouring interfaces are not part of the graph. The path of this resource "
                          + "is taken from the index of the interface graphs.")
    @ApiResponse(responseCode = "200", description = "The graph")
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @ApiResponse(responseCode = "404", description = "No interface with this id", content = @Content)
    @GetMapping("/interfaces/{interfaceId}")
    public @Nullable ResponseEntity<GraphWithFingerprintV2Dto> getInterfaceRelatedGraph(
            @Parameter(description = "The id of the interface node, as the index names it", example = "I14")
            @PathVariable String interfaceId, WebRequest request) {
        OptionalLong id = NodeV2Id.parseInterfaceId(interfaceId);
        if (id.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        GraphSnapshot snapshot = snapshot();
        String fingerprint = snapshot.v2().fingerprintOfInterface(id.getAsLong());
        if (etagSupport.isNotModified(request, etagSupport.entityTag(fingerprint))) {
            return null;
        }
        return answerOrNotFound(graphExtractor.getInterfaceRelatedGraph(snapshot.fullGraph(), id.getAsLong()),
                fingerprint);
    }

    /** The graph and the fingerprints this instance answers from - see {@link GraphSnapshot#required}. */
    private GraphSnapshot snapshot() {
        return GraphSnapshot.required(graphHolder.getSnapshot());
    }

    private ResponseEntity<GraphWithFingerprintV2Dto> answerOrNotFound(Graph subgraph,
                                                                       @Nullable String fingerprintFromSnapshot) {
        if (subgraph.nodes().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return answer(subgraph, fingerprintFromSnapshot);
    }

    /**
     * A graph and the fingerprint that covers it, tagged with that same fingerprint. The fingerprint comes from
     * the snapshot; it is computed here only if the snapshot has none for this resource, as in the API v1.
     */
    private ResponseEntity<GraphWithFingerprintV2Dto> answer(Graph domainGraph,
                                                             @Nullable String fingerprintFromSnapshot) {
        GraphV2Dto graph = GraphV2DtoMapper.map(domainGraph);
        String fingerprint = fingerprintFromSnapshot != null
                ? fingerprintFromSnapshot
                : fingerprintCalculator.calculate(graph);
        return etagSupport.ok(new GraphWithFingerprintV2Dto(graph, fingerprint),
                etagSupport.entityTag(fingerprint));
    }
}
