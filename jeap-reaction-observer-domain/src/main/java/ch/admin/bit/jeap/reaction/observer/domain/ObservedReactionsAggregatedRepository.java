package ch.admin.bit.jeap.reaction.observer.domain;

import java.time.LocalDate;
import java.util.List;

public interface ObservedReactionsAggregatedRepository {

    void aggregateObservedReactionsForDay(LocalDate date);

    List<ObservedReactionsAggregatedStatisticsV2> getStatistics(String component, LocalDate fromDate);

    void deleteAggregatedDataOlderThan(LocalDate date);
}
