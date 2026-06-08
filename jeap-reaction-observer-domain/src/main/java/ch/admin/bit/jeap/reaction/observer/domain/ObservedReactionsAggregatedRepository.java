package ch.admin.bit.jeap.reaction.observer.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

public interface ObservedReactionsAggregatedRepository {

    void aggregateObservedReactionsForDay(LocalDate date);

    void deleteAggregatedDataOlderThan(LocalDate date);

    Map<Long, Integer> getMedianPerReaction(LocalDate fromDate);

    Map<String, LocalDate> getLastObservedReactionDatePerComponent();

    /**
     * Returns the ids (reaction_fk) of all reactions that have been observed (i.e. have aggregated
     * observation data) on or after the given date. Reactions without recent observations are not
     * returned and are therefore no longer presented in the reaction graph (see JEAP-6459).
     */
    Set<Long> findReactionFksObservedSince(LocalDate fromDate);
}
