package ch.admin.bit.jeap.reaction.observer.domain;

import java.time.LocalDate;
import java.util.Map;

public interface ObservedReactionsAggregatedRepository {

    void aggregateObservedReactionsForDay(LocalDate date);

    void deleteAggregatedDataOlderThan(LocalDate date);

    Map<Long, Integer> getMedianPerReaction(LocalDate fromDate);
}
