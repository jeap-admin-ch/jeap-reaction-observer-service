package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatistics;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.time.LocalDate.now;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class StatisticsController {

    private final ReactionObserverProperties properties;
    private final ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;

    @PreAuthorize("hasAnyRole('reaction-observer-read')")
    @Operation(summary = "Get statistics")
    @GetMapping("/statistics/{component}")
    public ResponseEntity<List<ObservedReactionsAggregatedStatistics>> getStatisticsForComponent(@PathVariable String component) {
        return ResponseEntity.ok(observedReactionsAggregatedRepository.getStatistics(component, now().minusDays(properties.getStatisticsPeriodInDays())));
    }

    @PreAuthorize("hasAnyRole('reaction-observer-read')")
    @Operation(summary = "Get statistics")
    @GetMapping("/statisticsV2/{component}")
    public ResponseEntity<List<ObservedReactionsAggregatedStatistics>> getStatisticsV2ForComponent(@PathVariable String component) {
        return this.getStatisticsForComponent(component);
    }

}
