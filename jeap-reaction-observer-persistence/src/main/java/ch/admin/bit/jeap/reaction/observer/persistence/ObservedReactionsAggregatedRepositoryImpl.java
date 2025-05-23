package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatistics;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
class ObservedReactionsAggregatedRepositoryImpl implements ObservedReactionsAggregatedRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    @Override
    public void aggregateObservedReactionsForDay(LocalDate date) {
        this.jdbcTemplate.update("""
                INSERT INTO observed_reactions_aggregated (reaction_fk, component, trigger_id, trigger_type, trigger_fqn, action_id, action_type, action_fqn, date, count)
                SELECT obsreaction.reaction_fk as rid, r.component, r.trigger_id, r.trigger_type, r.trigger_fqn, r.action_id, r.action_type, r.action_fqn, obsreaction.observation_date, sum(obsreaction.count) 
                FROM observed_reaction obsreaction
                INNER JOIN reaction r on obsreaction.reaction_fk= r.id                                                             
                       WHERE obsreaction.observation_date = ?
                       GROUP BY obsreaction.reaction_fk, obsreaction.observation_date, r.component, r.trigger_id, r.trigger_type, r.trigger_fqn, r.action_id, r.action_type, r.action_fqn
                """, date);
    }

    @Override
    public List<ObservedReactionsAggregatedStatistics> getStatistics(String component, LocalDate fromDate) {
        String sql = """
                WITH base_stats AS (
                    SELECT
                        ora.component,
                        ora.trigger_type,
                        ora.trigger_fqn,
                        ora.action_type,
                        ora.action_fqn,
                        ora.reaction_fk,
                        SUM(ora.count) AS count,
                        CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY ora.count) AS FLOAT) AS median,
                        CASE WHEN ora.trigger_id IS NULL 
                            THEN NULL 
                        ELSE 
                            CAST((TRUNC(SUM(ora.count) * 100.0 / NULLIF(SUM(SUM(ora.count)) OVER (PARTITION BY ora.component, ora.trigger_id), 0), 2)) AS FLOAT) 
                        END AS percentage
                    FROM observed_reactions_aggregated ora
                    WHERE ora.component = ? AND ora.date >= ?
                    GROUP BY ora.component, ora.trigger_id, ora.action_id, ora.trigger_type, ora.trigger_fqn, ora.action_type, ora.action_fqn, ora.reaction_fk
                )
                SELECT 
                    bs.component,
                    bs.trigger_type,
                    bs.trigger_fqn,
                    bs.action_type,
                    bs.action_fqn,
                    bs.count,
                    bs.median,
                    bs.percentage,
                    bs.reaction_fk,
                    STRING_AGG(opt.property_key || '=' || opt.property_value, ',') AS trigger_properties,
                    STRING_AGG(opa.property_key || '=' || opa.property_value, ',') AS action_properties
                FROM base_stats bs
                LEFT JOIN observation_property opt ON bs.reaction_fk = opt.reaction_trigger_fk
                LEFT JOIN observation_property opa ON bs.reaction_fk = opa.reaction_action_fk
                GROUP BY bs.component, bs.trigger_type, bs.trigger_fqn, bs.action_type, bs.action_fqn, bs.count, bs.median, bs.percentage, bs.reaction_fk
                """;

        return this.jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    Double percentage = rs.getDouble("percentage");
                    if (rs.wasNull()) {
                        percentage = null;
                    }
                    return new ObservedReactionsAggregatedStatistics(
                            rs.getString("component"),
                            rs.getString("trigger_type"),
                            rs.getString("trigger_fqn"),
                            rs.getString("action_type"),
                            rs.getString("action_fqn"),
                            rs.getLong("count"),
                            rs.getDouble("median"),
                            percentage,
                            parseAsMap(rs.getString("trigger_properties")),
                            parseAsMap(rs.getString("action_properties"))
                    );
                },
                component, fromDate
        );

    }

    private Map<String, String> parseAsMap(String propsString) {
        if (propsString == null || propsString.isEmpty()) {
            return Map.of();
        }
        Map<String, String> triggerProperties = new HashMap<>();
        for (String prop : propsString.split(",")) {
            String[] keyValue = prop.split("=", 2);
            if (keyValue.length == 2) {
                triggerProperties.put(keyValue[0], keyValue[1]);
            }
        }
        return triggerProperties;
    }

    @Override
    public void deleteAggregatedDataOlderThan(LocalDate date) {
        this.jdbcTemplate.update("DELETE FROM observed_reactions_aggregated WHERE date < ?", date);
    }

}
