package ch.admin.bit.jeap.reaction.observer.domain;

import java.time.LocalDate;
import java.util.Map;

public interface ObservedReactionsAggregatedRepository {

    void aggregateObservedReactionsForDay(LocalDate date);

    void deleteAggregatedDataOlderThan(LocalDate date);

    /**
     * The median of the daily observation counts per reaction id (reaction_fk), of all reactions that have been
     * observed (i.e. have aggregated observation data) on or after the given date - also with a count of zero.
     * Reactions without recent observations are not returned and are therefore not presented in the
     * reaction graph.
     */
    Map<Long, Integer> getMedianPerReaction(LocalDate fromDate);

    Map<String, LocalDate> getLastObservedReactionDatePerComponent();
}
