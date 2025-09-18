package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphWithFingerprintDto;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphDtoMapper;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class GraphController {

    private final GraphHolder graphHolder;
    private final GraphFingerprintCalculator fingerprintCalculator;
    private final GraphExtractor graphExtractor;

    @PreAuthorize("hasAnyRole('reaction-observer-read')")
    @Operation(summary = "Get all reactions graph")
    @GetMapping("/graphs")
    public ResponseEntity<GraphWithFingerprintDto> getAllReactionsGraph() {
        var domainGraph = graphHolder.getGraph();
        var graphDto = GraphDtoMapper.map(domainGraph);
        var fingerprint = fingerprintCalculator.calculate(graphDto);

        var response = new GraphWithFingerprintDto(graphDto, fingerprint);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('reaction-observer-read')")
    @Operation(summary = "Get graph for a system")
    @GetMapping("/graphs/systems/{systemName}")
    public ResponseEntity<GraphWithFingerprintDto> getSystemRelatedGraph(@PathVariable String systemName) {

        var domainGraph = graphHolder.getGraph();
        var systemGraph = graphExtractor.getSystemRelatedGraph(domainGraph, systemName);

        if (systemGraph.nodes().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var systemGraphDto = GraphDtoMapper.map(systemGraph);
        var fingerprint = fingerprintCalculator.calculate(systemGraphDto);

        var response = new GraphWithFingerprintDto(systemGraphDto, fingerprint);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('reaction-observer-read')")
    @Operation(summary = "Get graph for a component")
    @GetMapping("/graphs/components/{componentName}")
    public ResponseEntity<GraphWithFingerprintDto> getComponentRelatedGraph(@PathVariable String componentName) {

        var domainGraph = graphHolder.getGraph();
        var componentGraph = graphExtractor.getComponentRelatedGraph(domainGraph, componentName);

        if (componentGraph.nodes().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var componentGraphDto = GraphDtoMapper.map(componentGraph);
        var fingerprint = fingerprintCalculator.calculate(GraphDtoMapper.map(componentGraph));

        var response = new GraphWithFingerprintDto(componentGraphDto, fingerprint);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('reaction-observer-read')")
    @Operation(summary = "Get variant graphs for a message type")
    @GetMapping("/graphs/messages/{messageType}")
    public ResponseEntity<Map<String, GraphWithFingerprintDto>> getMessageTypeRelatedGraphs(@PathVariable String messageType) {
        var domainGraph = graphHolder.getGraph();

        // Collect all variants (including null) for the given message type
        var variants = domainGraph.nodes().stream()
                .filter(node -> node instanceof Message message && message.messageType().equals(messageType))
                .map(node -> ((Message) node).variant()) // may be null
                .distinct()
                .toList();

        var result = variants.stream()
                .collect(Collectors.toMap(
                        variant -> variant == null ? messageType : messageType + "/" + variant,
                        variant -> {
                            var subgraph = graphExtractor.getMessageRelatedGraph(domainGraph, messageType, variant);
                            var dto = GraphDtoMapper.map(subgraph);
                            var fingerprint = fingerprintCalculator.calculate(dto);
                            return new GraphWithFingerprintDto(dto, fingerprint);
                        }
                ));

        return ResponseEntity.ok(result);
    }
}
