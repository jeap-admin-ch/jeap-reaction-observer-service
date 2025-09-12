package ch.admin.bit.jeap.reaction.observer.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ObservedReactionsAggregatedRepository {

    void aggregateObservedReactionsForDay(LocalDate date);

    List<ObservedReactionsAggregatedStatistics> getStatistics(String component, LocalDate fromDate);

    void deleteAggregatedDataOlderThan(LocalDate date);

    Map<Long, Integer> getMedianPerReaction(LocalDate fromDate);
}
