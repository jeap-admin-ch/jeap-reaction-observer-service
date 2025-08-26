package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.Action;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatisticsV2;
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
                INSERT INTO observed_reactions_aggregated (reaction_fk, component, trigger_id, trigger_type, trigger_fqn, action_id, action_type, action_fqn, date, count)
                SELECT obsreaction.reaction_fk as rid, r.component, r.trigger_id, r.trigger_type, r.trigger_fqn, r.action_id, r.action_type, r.action_fqn, obsreaction.observation_date, sum(obsreaction.count) 
                FROM observed_reaction obsreaction
                INNER JOIN reaction r on obsreaction.reaction_fk= r.id                                                             
                       WHERE obsreaction.observation_date = ?
                       GROUP BY obsreaction.reaction_fk, obsreaction.observation_date, r.component, r.trigger_id, r.trigger_type, r.trigger_fqn, r.action_id, r.action_type, r.action_fqn
                """, date);
    }

    @Override
    public List<ObservedReactionsAggregatedStatisticsV2> getStatistics(String component, LocalDate fromDate) {
        String sql = """
                WITH base_stats AS (
                    SELECT
                        ora.component,
                        ora.trigger_type,
                        ora.trigger_fqn,
                        ora.action_type as old_action_type,
                        ora.action_fqn as old_action_fqn,                        
                        ora.reaction_fk,
                        SUM(ora.count) AS count,
                        CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY ora.count) AS FLOAT) AS median,
                        CASE WHEN ora.trigger_id IS NULL 
                            THEN NULL 
                        ELSE 
                            CAST((TRUNC(SUM(ora.count) * 100.0 / NULLIF(SUM(SUM(ora.count)) OVER (PARTITION BY ora.trigger_id), 0), 2)) AS FLOAT) 
                        END AS percentage
                    FROM observed_reactions_aggregated ora
                    WHERE ora.component = ? AND ora.date >= ?
                    GROUP BY ora.component, ora.reaction_fk, ora.trigger_id, ora.action_id, ora.trigger_type, ora.trigger_fqn, ora.action_type, ora.action_fqn
                )
                SELECT 
                    bs.component,
                    bs.trigger_type,
                    bs.trigger_fqn,
                    act.action_type,
                    act.action_fqn,
                    bs.old_action_type,
                    bs.old_action_fqn,
                    bs.count,
                    bs.median,
                    bs.percentage,
                    bs.reaction_fk,
                    act.id as action_id,
                    STRING_AGG(opt.property_key || '=' || opt.property_value, ',') AS trigger_properties,
                    STRING_AGG(opa.property_key || '=' || opa.property_value, ',') AS old_action_properties,
                    STRING_AGG(prop.property_key || '=' || prop.property_value, ',') AS action_properties
                FROM base_stats bs
                LEFT JOIN observation_property opt ON bs.reaction_fk = opt.reaction_trigger_fk
                LEFT JOIN observation_property opa ON bs.reaction_fk = opa.reaction_action_fk
                LEFT JOIN action act ON bs.reaction_fk = act.reaction_id
                LEFT JOIN observation_property prop on prop.action_fk=act.id
                GROUP BY bs.component, bs.trigger_type, bs.trigger_fqn, bs.old_action_type, bs.old_action_fqn, act.action_type, act.action_fqn, bs.count, bs.median, bs.percentage, bs.reaction_fk, act.id
                ORDER BY bs.reaction_fk DESC
                """;

        Collection<ObservedReactionsAggregatedStatisticsV2> query = this.jdbcTemplate.query(
                sql,
                rs -> {
                    Map<Long, ObservedReactionsAggregatedStatisticsV2> statisticsMap = new LinkedHashMap<>();

                    while (rs.next()) {
                        Long reactionFk = rs.getLong("reaction_fk");
                        ObservedReactionsAggregatedStatisticsV2 statistics = statisticsMap.computeIfAbsent(reactionFk, k -> {
                            try {
                                String triggerType = rs.getString("trigger_type");
                                Double percentage = rs.getDouble("percentage");
                                if (rs.wasNull() || triggerType == null) {
                                    percentage = null;
                                }
                                return new ObservedReactionsAggregatedStatisticsV2(
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
                        String actionType = rs.getString("old_action_type");
                        String actionFqn = rs.getString("old_action_fqn");
                        if (actionType != null && !actionType.isEmpty()) {
                            //Enable backward compatibility for old action properties
                            actionProperties = parseAsMap(rs.getString("old_action_properties"));
                            statistics.actions().add(new Action(
                                    actionType,
                                    actionFqn,
                                    actionProperties
                            ));
                        } else {
                            Long actionId = rs.getLong("action_id");
                            if (!rs.wasNull()) {
                                actionType = rs.getString("action_type");
                                actionFqn = rs.getString("action_fqn");
                                actionProperties = parseAsMap(rs.getString("action_properties"));
                                statistics.actions().add(new Action(
                                        actionType,
                                        actionFqn,
                                        actionProperties
                                ));
                            }
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
