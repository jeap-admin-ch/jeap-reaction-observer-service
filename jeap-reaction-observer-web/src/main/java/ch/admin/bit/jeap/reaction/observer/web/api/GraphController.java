package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshot;
import ch.admin.bit.jeap.reaction.observer.web.MessageGraphKey;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphWithFingerprintDto;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphDtoMapper;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<GraphWithFingerprintDto> getAllReactionsGraph(WebRequest request) {
        String knownTag = etagSupport.entityTag(snapshot().graphFingerprint());
        if (etagSupport.isNotModified(request, knownTag)) {
            return null;
        }
        var domainGraph = graphHolder.getGraph();
        var graphDto = GraphDtoMapper.map(domainGraph);
        var fingerprint = fingerprintCalculator.calculate(graphDto);

        return etagSupport.respond(request, new GraphWithFingerprintDto(graphDto, fingerprint),
                etagSupport.entityTag(fingerprint));
    }

    @PreAuthorize("@reactionsApiAuthorization.canRead()")
    @Operation(summary = "Get graph for a system")
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping("/graphs/systems/{systemName}")
    public ResponseEntity<GraphWithFingerprintDto> getSystemRelatedGraph(@PathVariable String systemName,
                                                                         WebRequest request) {
        String knownTag = etagSupport.entityTag(snapshot().fingerprintOfSystem(systemName));
        if (etagSupport.isNotModified(request, knownTag)) {
            return null;
        }

        var domainGraph = graphHolder.getGraph();
        var systemGraph = graphExtractor.getSystemRelatedGraph(domainGraph, systemName);

        if (systemGraph.nodes().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var systemGraphDto = GraphDtoMapper.map(systemGraph);
        var fingerprint = fingerprintCalculator.calculate(systemGraphDto);

        return etagSupport.respond(request, new GraphWithFingerprintDto(systemGraphDto, fingerprint),
                etagSupport.entityTag(fingerprint));
    }

    @PreAuthorize("@reactionsApiAuthorization.canRead()")
    @Operation(summary = "Get graph for a component")
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping("/graphs/components/{componentName}")
    public ResponseEntity<GraphWithFingerprintDto> getComponentRelatedGraph(@PathVariable String componentName,
                                                                            WebRequest request) {
        String knownTag =
                etagSupport.entityTag(snapshot().fingerprintOfComponent(componentName));
        if (etagSupport.isNotModified(request, knownTag)) {
            return null;
        }

        var domainGraph = graphHolder.getGraph();
        var componentGraph = graphExtractor.getComponentRelatedGraph(domainGraph, componentName);

        if (componentGraph.nodes().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var componentGraphDto = GraphDtoMapper.map(componentGraph);
        var fingerprint = fingerprintCalculator.calculate(componentGraphDto);

        return etagSupport.respond(request, new GraphWithFingerprintDto(componentGraphDto, fingerprint),
                etagSupport.entityTag(fingerprint));
    }

    @PreAuthorize("@reactionsApiAuthorization.canRead()")
    @Operation(summary = "Get variant graphs for a message type")
    @ApiResponse(responseCode = "304", description = "If-None-Match matched", content = @Content)
    @GetMapping("/graphs/messages/{messageType}")
    public ResponseEntity<Map<String, GraphWithFingerprintDto>> getMessageTypeRelatedGraphs(
            @PathVariable String messageType, WebRequest request) {
        String knownTag =
                etagSupport.entityTag(snapshot().fingerprintOfMessageType(messageType));
        if (etagSupport.isNotModified(request, knownTag)) {
            return null;
        }

        Graph domainGraph = graphHolder.getGraph();

        // Collect all variants (including null) for the given message type
        var variants = domainGraph.nodes().stream()
                .filter(node -> node instanceof Message message && message.messageType().equals(messageType))
                .map(node -> ((Message) node).variant()) // may be null
                .distinct()
                .toList();

        var result = variants.stream()
                .collect(Collectors.toMap(
                        variant -> MessageGraphKey.of(messageType, variant),
                        variant -> {
                            var subgraph = graphExtractor.getMessageRelatedGraph(domainGraph, messageType,
                                    variant);
                            var dto = GraphDtoMapper.map(subgraph);
                            var fingerprint = fingerprintCalculator.calculate(dto);
                            return new GraphWithFingerprintDto(dto, fingerprint);
                        }
                ));

        // The tag of this resource covers every variant it answers with, so it is the snapshot's - which is
        // the same value the index lists. Recomputing it from the result would duplicate that rule.
        return etagSupport.respond(request, result, knownTag);
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
