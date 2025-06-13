package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.Action;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatistics;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatisticsV2;
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

import java.util.Collections;
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
        List<ObservedReactionsAggregatedStatisticsV2> statistics = observedReactionsAggregatedRepository.getStatistics(component, now().minusDays(properties.getStatisticsPeriodInDays()));
        // Transform V2 statistics to V1 format for backwards compatibility, supporting up to one action and
        // ignoring the rest
        List<ObservedReactionsAggregatedStatistics> statisticsV1 = statistics.stream().map(v2 -> {
            Action action = v2.actions() != null && !v2.actions().isEmpty() ? v2.actions().getFirst() : null;
            return new ObservedReactionsAggregatedStatistics(
                    v2.component(),
                    v2.triggerType(),
                    v2.triggerFqn(),
                    action != null ? action.actionType() : null,
                    action != null ? action.actionFqn() : null,
                    v2.count(),
                    v2.median(),
                    v2.percentage(),
                    v2.triggerProperties(),
                    action != null ? action.actionProperties() : Collections.emptyMap()
            );
        }).toList();
        return ResponseEntity.ok(statisticsV1);
    }

    @PreAuthorize("hasAnyRole('reaction-observer-read')")
    @Operation(summary = "Get statistics")
    @GetMapping("/statisticsV2/{component}")
    public ResponseEntity<List<ObservedReactionsAggregatedStatisticsV2>> getStatisticsV2ForComponent(@PathVariable String component) {
        return ResponseEntity.ok(observedReactionsAggregatedRepository.getStatistics(component, now().minusDays(properties.getStatisticsPeriodInDays())));
    }

}
