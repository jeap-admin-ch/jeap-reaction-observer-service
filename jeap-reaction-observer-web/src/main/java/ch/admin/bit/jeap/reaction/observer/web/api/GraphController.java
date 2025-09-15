package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
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
    public ResponseEntity<GraphWithFingerprintDto> getAllReactionsGraph(@PathVariable String systemName) {

        var domainGraph = graphHolder.getGraph();
        var systemGraph = graphExtractor.getSystemRelatedGraph(domainGraph, systemName);

        if (systemGraph == null || systemGraph.nodes().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var systemGraphDto = GraphDtoMapper.map(systemGraph);
        var fingerprint = fingerprintCalculator.calculate(GraphDtoMapper.map(domainGraph));

        var response = new GraphWithFingerprintDto(systemGraphDto, fingerprint);
        return ResponseEntity.ok(response);
    }
}
