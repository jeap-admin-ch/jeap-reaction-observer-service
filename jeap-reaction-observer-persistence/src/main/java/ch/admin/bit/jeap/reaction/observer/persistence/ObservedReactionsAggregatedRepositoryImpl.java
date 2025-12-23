package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
class ObservedReactionsAggregatedRepositoryImpl implements ObservedReactionsAggregatedRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    @Override
    public void aggregateObservedReactionsForDay(LocalDate date) {
        this.jdbcTemplate.update("""
                INSERT INTO observed_reactions_aggregated (reaction_fk, component, date, count)
                SELECT obsreaction.reaction_fk, r.component, obsreaction.observation_date, sum(obsreaction.count)
                FROM observed_reaction obsreaction
                INNER JOIN reaction r on obsreaction.reaction_fk= r.id                                                             
                       WHERE obsreaction.observation_date = ?
                       GROUP BY obsreaction.reaction_fk, obsreaction.observation_date, r.component
                """, date);
    }

    @Transactional
    @Override
    public void deleteAggregatedDataOlderThan(LocalDate date) {
        this.jdbcTemplate.update("DELETE FROM observed_reactions_aggregated WHERE date < ?", date);
    }

    @Override
    public Map<Long, Integer> getMedianPerReaction(LocalDate fromDate) {
        String sql = """
                SELECT reaction_fk,
                       CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY count) AS INTEGER) AS median
                FROM observed_reactions_aggregated
                WHERE date >= ?
                GROUP BY reaction_fk
                """;

        return jdbcTemplate.query(sql, rs -> {
            Map<Long, Integer> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("reaction_fk"), rs.getInt("median"));
            }
            return result;
        }, fromDate);
    }
}
