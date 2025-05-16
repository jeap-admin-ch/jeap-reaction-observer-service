package ch.admin.bit.jeap.reaction.observer.domain.aggregation;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@AllArgsConstructor
@Component
@Slf4j
public class AggregationService {

    private final ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;

    public void aggregateData(LocalDate date) {
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(date);
    }

    public void deleteAggregatedDataOlderThan(LocalDate date) {
        observedReactionsAggregatedRepository.deleteAggregatedDataOlderThan(date);
    }
}
