package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatistics;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@AllArgsConstructor
class ObservedReactionsAggregatedRepositoryImpl implements ObservedReactionsAggregatedRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    @Override
    public void aggregateObservedReactionsForDay(LocalDate date) {
        this.jdbcTemplate.update("""
                INSERT INTO observed_reactions_aggregated (reaction_fk, component, trigger_type, trigger_fqn, action_type, action_fqn, date, count)
                SELECT obsreaction.reaction_fk as rid, r.component, r.trigger_type, r.trigger_fqn, r.action_type, r.action_fqn, CAST(obsreaction.timeframe_start AS DATE), sum(obsreaction.count) FROM observed_reaction obsreaction
                INNER JOIN reaction r on obsreaction.reaction_fk= r.id                                                             
                       WHERE CAST(obsreaction.timeframe_start AS DATE) = ?
                       GROUP BY obsreaction.reaction_fk, CAST(obsreaction.timeframe_start AS DATE)
                """, date);
    }

    @Override
    public List<ObservedReactionsAggregatedStatistics> getStatistics(String component, LocalDate fromDate) {
        return this.jdbcTemplate.query("""
                SELECT
                    component,
                    trigger_type,
                    trigger_fqn,
                    action_type,
                    action_fqn,
                    SUM(count) AS count,
                    CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY count) AS FLOAT) AS median,
                    CAST((TRUNC(SUM(count) * 100.0 / SUM(SUM(count)) OVER (PARTITION BY component, trigger_type, trigger_fqn), 2)) AS FLOAT) AS percentage
                FROM observed_reactions_aggregated
                WHERE component = ? and date >= ?
                GROUP BY component, trigger_type, trigger_fqn, action_type, action_fqn
                """,
                (rs, rowNum) -> new ObservedReactionsAggregatedStatistics(
                        rs.getString("component"),
                        rs.getString("trigger_type"),
                        rs.getString("trigger_fqn"),
                        rs.getString("action_type"),
                        rs.getString("action_fqn"),
                        rs.getInt("count"),
                        rs.getDouble("median"),
                        rs.getDouble("percentage")
                ),
                component, fromDate
        );

    }

    @Override
    public void deleteAggregatedDataOlderThan(LocalDate date) {
        this.jdbcTemplate.update("DELETE FROM observed_reactions_aggregated WHERE date < ?", date);
    }

}
