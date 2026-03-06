package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class StatisticsController {

    private final ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;

    @PreAuthorize("hasAnyRole('reaction-observer-read')")
    @Operation(summary = "Get statistics")
    @GetMapping("/last-observation-date")
    public ResponseEntity<Map<String, LocalDate>> getLastObservedReactionDatePerComponent() {
        return ResponseEntity.ok(observedReactionsAggregatedRepository.getLastObservedReactionDatePerComponent());
    }

}
