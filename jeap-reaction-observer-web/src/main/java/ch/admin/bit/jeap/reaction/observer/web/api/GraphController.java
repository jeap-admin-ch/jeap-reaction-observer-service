package ch.admin.bit.jeap.reaction.observer.web.api;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class GraphController {

    private final GraphHolder graphHolder;
    private final GraphFingerprintCalculator fingerprintCalculator;

    @PreAuthorize("hasAnyRole('reaction-observer-read')")
    @Operation(summary = "Get graph")
    @GetMapping("/graphs")
    public ResponseEntity<GraphWithFingerprintDto> getAllReactionsGraph() {
        var domainGraph = graphHolder.getGraph();
        var graphDto = GraphDtoMapper.map(domainGraph);
        var fingerprint = fingerprintCalculator.calculate(graphDto);

        var response = new GraphWithFingerprintDto(graphDto, fingerprint);
        return ResponseEntity.ok(response);
    }
}

