package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatistics;
import ch.admin.bit.jeap.reaction.observer.domain.models.Action;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

@Component
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


    @Override
    public List<ObservedReactionsAggregatedStatistics> getStatistics(String component, LocalDate fromDate) {
        String sql = """
                WITH base_stats AS (
                    SELECT
                        ora.component,
                        i_trigger.type AS trigger_type,
                        i_trigger.fqn AS trigger_fqn,
                        ora.reaction_fk,
                        SUM(ora.count) AS count,
                        CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY ora.count) AS FLOAT) AS median,
                        CASE WHEN r.trigger_id IS NULL
                            THEN NULL
                        ELSE
                            CAST((TRUNC(SUM(ora.count) * 100.0 / NULLIF(SUM(SUM(ora.count)) OVER (PARTITION BY r.trigger_id), 0), 2)) AS FLOAT)
                        END AS percentage
                    FROM observed_reactions_aggregated ora
                    INNER JOIN reaction r ON r.id = ora.reaction_fk
                    LEFT JOIN interface i_trigger ON r.interface_id = i_trigger.id
                    WHERE ora.component = ? AND ora.date >= ?
                    GROUP BY ora.component, ora.reaction_fk, r.trigger_id, i_trigger.type, i_trigger.fqn
                )
                SELECT 
                    bs.component,
                    bs.trigger_type,
                    bs.trigger_fqn,
                    i_action.type AS action_type,
                    i_action.fqn AS action_fqn,
                    bs.count,
                    bs.median,
                    bs.percentage,
                    bs.reaction_fk,
                    act.id AS action_id,
                    STRING_AGG(opt.property_key || '=' || opt.property_value, ',') AS trigger_properties,
                    STRING_AGG(prop.property_key || '=' || prop.property_value, ',') AS action_properties
                FROM base_stats bs
                LEFT JOIN observation_property opt ON bs.reaction_fk = opt.reaction_trigger_fk
                LEFT JOIN action act ON bs.reaction_fk = act.reaction_id
                LEFT JOIN interface i_action ON act.interface_id = i_action.id
                LEFT JOIN observation_property prop ON prop.action_fk = act.id
                GROUP BY bs.component, bs.trigger_type, bs.trigger_fqn, i_action.type, i_action.fqn, bs.count, bs.median, bs.percentage, bs.reaction_fk, act.id
                ORDER BY bs.reaction_fk DESC
                """;

        Collection<ObservedReactionsAggregatedStatistics> query = this.jdbcTemplate.query(
                sql,
                rs -> {
                    Map<Long, ObservedReactionsAggregatedStatistics> statisticsMap = new LinkedHashMap<>();

                    while (rs.next()) {
                        Long reactionFk = rs.getLong("reaction_fk");
                        ObservedReactionsAggregatedStatistics statistics = statisticsMap.computeIfAbsent(reactionFk, k -> {
                            try {
                                String triggerType = rs.getString("trigger_type");
                                Double percentage = rs.getDouble("percentage");
                                if (rs.wasNull() || triggerType == null) {
                                    percentage = null;
                                }
                                return new ObservedReactionsAggregatedStatistics(
                                        rs.getString("component"),
                                        triggerType,
                                        rs.getString("trigger_fqn"),
                                        new ArrayList<>(),
                                        rs.getLong("count"),
                                        rs.getDouble("median"),
                                        percentage,
                                        parseAsMap(rs.getString("trigger_properties"))
                                );
                            } catch (SQLException e) {
                                throw new DataAccessException("Error processing result set", e) {
                                };
                            }
                        });
                        Map<String, String> actionProperties;
                        rs.getLong("action_id");
                        if (!rs.wasNull()) {
                            String actionType = rs.getString("action_type");
                            String actionFqn = rs.getString("action_fqn");
                            actionProperties = parseAsMap(rs.getString("action_properties"));
                            statistics.actions().add(new Action(
                                    actionType,
                                    actionFqn,
                                    actionProperties
                            ));
                        }
                    }

                    return statisticsMap.values();
                },
                component, fromDate
        );
        return new ArrayList<>(query == null ? Collections.emptyList() : query);
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

    @Transactional
    @Override
    public void deleteAggregatedDataOlderThan(LocalDate date) {
        this.jdbcTemplate.update("DELETE FROM observed_reactions_aggregated WHERE date < ?", date);
    }

}
