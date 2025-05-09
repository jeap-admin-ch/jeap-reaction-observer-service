package ch.admin.bit.jeap.reaction.observer.persistence;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
interface JpaObservedReactionsAggregatedRepository extends CrudRepository<ObservedReactionsAggregatedEntity, Long> {

    @Query(nativeQuery = true, value = """
            SELECT
                component,
                trigger_type,
                trigger_fqn,
                action_type,
                action_fqn,
                SUM(count) AS count,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY count) AS median,
                (TRUNC(SUM(count) * 100.0 / SUM(SUM(count)) OVER (PARTITION BY component, trigger_type, trigger_fqn), 2)) AS percentage
            FROM observed_reactions_aggregated
            WHERE component = :component and date >= :fromDate         
            GROUP BY component, trigger_type, trigger_fqn, action_type, action_fqn
            """)
    List<Object[]> getStatistics(String component, LocalDate fromDate);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO observed_reactions_aggregated (reaction_fk, component, trigger_type, trigger_fqn, action_type, action_fqn, date, count)
            SELECT obsreaction.reaction_fk as rid, r.component, r.trigger_type, r.trigger_fqn, r.action_type, r.action_fqn, CAST(obsreaction.timeframe_start AS DATE), sum(obsreaction.count) FROM observed_reaction obsreaction
            INNER JOIN reaction r on obsreaction.reaction_fk= r.id                                                             
                   WHERE CAST(obsreaction.timeframe_start AS DATE) = :date
                   GROUP BY obsreaction.reaction_fk, CAST(obsreaction.timeframe_start AS DATE)
            """)

    void aggregateForDate(LocalDate date);

    void deleteByDateBefore(LocalDate date);
}
