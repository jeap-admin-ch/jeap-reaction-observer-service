package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshot;
import ch.admin.bit.jeap.reaction.observer.web.MessageGraphKey;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphWithFingerprintDto;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphDtoMapper;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * The reaction graphs: the whole graph, and the subgraph of one system, component or message type.
 * <p>
 * Every one of them carries an {@code ETag} - the fingerprint the body also holds - and honours
 * {@code If-None-Match} with a {@code 304}. A consumer that got the tag from an index
 * ({@link GraphIndexController}) therefore knows before it asks whether the answer would be the same, and a
 * conditional request that matches is answered <b>without extracting or serializing the graph</b>: the tag
 * comes from the snapshot built when the graph was refreshed.
 * <p>
 * <b>Every handler reads the snapshot exactly once</b>, and answers the tag and the body from that one
 * object. Reading it twice could straddle a refresh and tag one graph with another's fingerprint - which a
 * consumer would store and then be answered {@code 304} for content it never received.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class GraphController {

    private final GraphHolder graphHolder;
    private final GraphFingerprintCalculator fingerprintCalculator;
    private final GraphExtractor graphExtractor;
    private final EtagSupport etagSupport;

    @PreAuthorize("@reactionsApiAuthorization.canRead()")
    @Operation(summary = "Get all reactions graph")
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping("/graphs")
    public @Nullable ResponseEntity<GraphWithFingerprintDto> getAllReactionsGraph(WebRequest request) {
        GraphSnapshot snapshot = graphHolder.getSnapshot();
        String fingerprint = snapshot.graphFingerprint();
        if (etagSupport.isNotModified(request, etagSupport.entityTag(fingerprint))) {
            return null;
        }
        return answer(GraphDtoMapper.map(snapshot.graph()), fingerprint);
    }

    @PreAuthorize("@reactionsApiAuthorization.canRead()")
    @Operation(summary = "Get graph for a system")
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping("/graphs/systems/{systemName}")
    public @Nullable ResponseEntity<GraphWithFingerprintDto> getSystemRelatedGraph(
            @PathVariable String systemName, WebRequest request) {
        GraphSnapshot snapshot = graphHolder.getSnapshot();
        String fingerprint = snapshot.fingerprintOfSystem(systemName);
        if (etagSupport.isNotModified(request, etagSupport.entityTag(fingerprint))) {
            return null;
        }

        Graph systemGraph = graphExtractor.getSystemRelatedGraph(snapshot.graph(), systemName);
        if (systemGraph.nodes().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return answer(GraphDtoMapper.map(systemGraph), fingerprint);
    }

    @PreAuthorize("@reactionsApiAuthorization.canRead()")
    @Operation(summary = "Get graph for a component")
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping("/graphs/components/{componentName}")
    public @Nullable ResponseEntity<GraphWithFingerprintDto> getComponentRelatedGraph(
            @PathVariable String componentName, WebRequest request) {
        GraphSnapshot snapshot = graphHolder.getSnapshot();
        String fingerprint = snapshot.fingerprintOfComponent(componentName);
        if (etagSupport.isNotModified(request, etagSupport.entityTag(fingerprint))) {
            return null;
        }

        Graph componentGraph = graphExtractor.getComponentRelatedGraph(snapshot.graph(), componentName);
        if (componentGraph.nodes().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return answer(GraphDtoMapper.map(componentGraph), fingerprint);
    }

    @PreAuthorize("@reactionsApiAuthorization.canRead()")
    @Operation(summary = "Get variant graphs for a message type")
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping("/graphs/messages/{messageType}")
    public @Nullable ResponseEntity<Map<String, GraphWithFingerprintDto>> getMessageTypeRelatedGraphs(
            @PathVariable String messageType, WebRequest request) {
        GraphSnapshot snapshot = graphHolder.getSnapshot();
        // The tag of this resource covers every variant it answers with, which is what the snapshot combined
        // when it was built - and the same value the index lists
        String entityTag = etagSupport.entityTag(snapshot.fingerprintOfMessageType(messageType));
        if (etagSupport.isNotModified(request, entityTag)) {
            return null;
        }

        Graph domainGraph = snapshot.graph();

        // Collect all variants (including null) for the given message type
        var variants = domainGraph.nodes().stream()
                .filter(node -> node instanceof Message message && message.messageType().equals(messageType))
                .map(node -> ((Message) node).variant()) // may be null
                .distinct()
                .toList();

        Map<String, GraphWithFingerprintDto> result = variants.stream()
                .collect(Collectors.toMap(
                        variant -> MessageGraphKey.of(messageType, variant),
                        variant -> {
                            var subgraph = graphExtractor.getMessageRelatedGraph(domainGraph, messageType,
                                    variant);
                            var dto = GraphDtoMapper.map(subgraph);
                            return new GraphWithFingerprintDto(dto, fingerprintCalculator.calculate(dto));
                        }
                ));

        return etagSupport.ok(result, entityTag);
    }

    /**
     * A graph and the fingerprint that covers it, tagged with that same fingerprint.
     * <p>
     * The fingerprint comes from the snapshot, which computed it when the graph was refreshed. It is
     * recomputed here only if the snapshot has none for this resource - which cannot happen for a subgraph
     * that was found, since both come from the same graph, and is cheap insurance against them ever drifting
     * apart.
     */
    private ResponseEntity<GraphWithFingerprintDto> answer(GraphDto graph,
                                                           @Nullable String fingerprintFromSnapshot) {
        String fingerprint = fingerprintFromSnapshot != null
                ? fingerprintFromSnapshot
                : fingerprintCalculator.calculate(graph);
        return etagSupport.ok(new GraphWithFingerprintDto(graph, fingerprint),
                etagSupport.entityTag(fingerprint));
    }
}
