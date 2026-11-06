package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
@SecurityRequirement(name = "OIDC")
public class StatisticsController {

    private final ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;

    @PreAuthorize("hasRole('reactions', 'read')")
    @Operation(summary = "Get statistics")
    @GetMapping("/last-observation-date")
    public ResponseEntity<Map<String, LocalDate>> getLastObservedReactionDatePerComponent() {
        return ResponseEntity.ok(observedReactionsAggregatedRepository.getLastObservedReactionDatePerComponent());
    }

}
