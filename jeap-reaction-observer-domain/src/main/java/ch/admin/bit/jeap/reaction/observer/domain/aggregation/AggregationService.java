package ch.admin.bit.jeap.reaction.observer.domain.aggregation;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import io.micrometer.core.annotation.Timed;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@AllArgsConstructor
@Component
@Slf4j
public class AggregationService {

    private final ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;

    @Timed("reaction_observer_service_aggregate_data")
    public void aggregateData(LocalDate date) {
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(date);
    }

    public void deleteAggregatedDataOlderThan(LocalDate date) {
        observedReactionsAggregatedRepository.deleteAggregatedDataOlderThan(date);
    }
}
