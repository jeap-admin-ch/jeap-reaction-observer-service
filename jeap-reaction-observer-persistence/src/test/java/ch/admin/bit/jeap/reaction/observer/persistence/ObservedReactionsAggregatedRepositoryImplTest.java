package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.*;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getStartOfDay;
import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Slf4j
class ObservedReactionsAggregatedRepositoryImplTest {

    @Autowired
    private JpaReactionRepository jpaReactionRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private ObservedReactionRepository observedReactionRepository;

    @Autowired
    private ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aggregation_statistics_single_day() {
        String reactionId = "triggerId0#actionId0";
        String component = "component1";
        ZonedDateTime startOfDay = getStartOfDay();
        Map<String, String> triggerProps = Map.of("triggerKey1", "triggerValue1", "triggerKey2", "triggerValue2");
        Map<String, String> actionProps = Map.of("actionKey1", "actionValue1", "actionKey2", "actionValue2");
        Reaction reaction = new Reaction(component, reactionId,
                new Observation("triggerType", "triggerFqn", triggerProps),
                new Observation("actionType", "actionFqn", actionProps),
                startOfDay);
        reactionRepository.save(reaction);

        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay, startOfDay.plusHours(1)), 3));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(1), startOfDay.plusHours(2)), 5));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(10), startOfDay.plusHours(11)), 7));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());

        List<ObservedReactionsAggregatedStatistics> statistics = observedReactionsAggregatedRepository.getStatistics(component, getToday().minusDays(1L));
        assertEquals(1, statistics.size());
        ObservedReactionsAggregatedStatistics statisticsEntry = statistics.getFirst();
        assertEquals("component1", statisticsEntry.component());
        assertEquals("triggerType", statisticsEntry.triggerType());
        assertEquals("triggerFqn", statisticsEntry.triggerFqn());
        assertEquals("actionType", statisticsEntry.actionType());
        assertEquals("actionFqn", statisticsEntry.actionFqn());
        assertEquals(15, statisticsEntry.count());
        assertEquals(15f, statisticsEntry.median());
        assertEquals(100.00, statisticsEntry.percentage());

        // Verify trigger properties
        assertThat(statisticsEntry.triggerProperties()).isNotNull();
        assertEquals(2, statisticsEntry.triggerProperties().size());
        assertEquals("triggerValue1", statisticsEntry.triggerProperties().get("triggerKey1"));
        assertEquals("triggerValue2", statisticsEntry.triggerProperties().get("triggerKey2"));

        // Verify action properties
        assertThat(statisticsEntry.actionProperties()).isNotNull();
        assertEquals(2, statisticsEntry.actionProperties().size());
        assertEquals("actionValue1", statisticsEntry.actionProperties().get("actionKey1"));
        assertEquals("actionValue2", statisticsEntry.actionProperties().get("actionKey2"));
    }

    @Test
    void aggregation_statistics_different_days() {
        String reactionId = "triggerId1#actionId1";
        String component = "component2";
        ZonedDateTime startOfDay = getStartOfDay();
        ZonedDateTime yesterday = startOfDay.minusDays(1);
        ZonedDateTime theDayBefore = yesterday.minusDays(1);
        Reaction reaction = new Reaction(component, reactionId,
                new Observation("triggerType", "triggerFqn", Map.of()),
                new Observation("actionType", "actionFqn", Map.of()),
                startOfDay);
        reactionRepository.save(reaction);

        save(new ObservedReaction(component, reactionId, new Timeframe(theDayBefore, theDayBefore.plusHours(1)), 3));
        save(new ObservedReaction(component, reactionId, new Timeframe(yesterday.plusHours(1), yesterday.plusHours(2)), 5));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(10), startOfDay.plusHours(11)), 7));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.minusDays(31), startOfDay.minusDays(31).plusHours(11)), 7)); //Out of range, won't be considered


        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(startOfDay.minusDays(31).toLocalDate()); //Out of range, won't be considered
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(theDayBefore.toLocalDate());
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(yesterday.toLocalDate());
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());


        List<ObservedReactionsAggregatedStatistics> statistics = observedReactionsAggregatedRepository.getStatistics(component, getToday().minusDays(30L));
        assertEquals(1, statistics.size());
        ObservedReactionsAggregatedStatistics statisticsEntry = statistics.getFirst();
        assertEquals("component2", statisticsEntry.component());
        assertEquals("triggerType", statisticsEntry.triggerType());
        assertEquals("triggerFqn", statisticsEntry.triggerFqn());
        assertEquals(Collections.emptyMap(), statisticsEntry.actionProperties());
        assertEquals(Collections.emptyMap(), statisticsEntry.triggerProperties());
        assertEquals("actionType", statisticsEntry.actionType());
        assertEquals("actionFqn", statisticsEntry.actionFqn());
        assertEquals(15, statisticsEntry.count());
        assertEquals(5f, statisticsEntry.median());
        assertEquals(100.00, statisticsEntry.percentage());
    }

    @Test
    void aggregation_statistics_two_different_actions_same_trigger() {
        String component = "component1";
        Reaction reaction = createReaction(component, "triggerId2#actionId0", "triggerType", "triggerFqn", "actionType", "actionFqn");
        Reaction reaction1 = createReaction(component, "triggerId2#actionId1", "triggerType", "triggerFqn", "actionType1", "actionFqn1");
        reactionRepository.save(reaction);
        reactionRepository.save(reaction1);

        ZonedDateTime startOfDay = getStartOfDay();
        ZonedDateTime yesterday = startOfDay.minusDays(1);
        ZonedDateTime theDayBefore = yesterday.minusDays(1);

        save(new ObservedReaction(component, "triggerId2#actionId0", new Timeframe(theDayBefore, theDayBefore.plusHours(1)), 60));
        save(new ObservedReaction(component, "triggerId2#actionId0", new Timeframe(yesterday, yesterday.plusHours(1)), 10));
        save(new ObservedReaction(component, "triggerId2#actionId0", new Timeframe(startOfDay, startOfDay.plusHours(1)), 10));
        save(new ObservedReaction(component, "triggerId2#actionId1", new Timeframe(startOfDay, startOfDay.plusHours(1)), 20));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(theDayBefore.toLocalDate());
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(yesterday.toLocalDate());
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());

        List<ObservedReactionsAggregatedStatistics> statistics = observedReactionsAggregatedRepository.getStatistics(component, getToday().minusDays(30));
        assertEquals(2, statistics.size());
        ObservedReactionsAggregatedStatistics statisticsFirstReaction = statistics.getFirst();
        assertEquals(80L, statisticsFirstReaction.count());
        assertEquals(10f, statisticsFirstReaction.median());
        assertEquals(80.00, statisticsFirstReaction.percentage());

        ObservedReactionsAggregatedStatistics statisticsSecondReaction = statistics.getLast();
        assertEquals(20L, statisticsSecondReaction.count());
        assertEquals(20f, statisticsSecondReaction.median());
        assertEquals(20.00, statisticsSecondReaction.percentage());
    }

    @Test
    void aggregation_statistics_same_reactions_different_components() {
        String reactionId = "triggerId3#actionId0";
        Reaction reaction = createReaction("component1", reactionId, "triggerType", "triggerFqn", "actionType", "actionFqn");
        Reaction reaction1 = createReaction("component2", reactionId, "triggerType", "triggerFqn", "actionType", "actionFqn");
        reactionRepository.save(reaction);
        reactionRepository.save(reaction1);

        save(new ObservedReaction("component1", reactionId, new Timeframe(getStartOfDay(), getStartOfDay().plusHours(1)), 10));
        save(new ObservedReaction("component2", reactionId, new Timeframe(getStartOfDay(), getStartOfDay().plusHours(1)), 20));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());

        List<ObservedReactionsAggregatedStatistics> statistics = observedReactionsAggregatedRepository.getStatistics("component1", getToday().minusDays(30));
        assertEquals(1, statistics.size());
        ObservedReactionsAggregatedStatistics statisticsFirstReaction = statistics.getFirst();
        assertEquals(10L, statisticsFirstReaction.count());
        assertEquals(10f, statisticsFirstReaction.median());
        assertEquals(100.00, statisticsFirstReaction.percentage());

        statistics = observedReactionsAggregatedRepository.getStatistics("component2", getToday().minusDays(30));
        assertEquals(1, statistics.size());
        statisticsFirstReaction = statistics.getFirst();
        assertEquals(20L, statisticsFirstReaction.count());
        assertEquals(20f, statisticsFirstReaction.median());
        assertEquals(100.00, statisticsFirstReaction.percentage());
    }

    @Test
    void delete_aggregated_data() throws SQLException {
        // given: a reaction and several observations over different periods
        String reactionId = "reactionId4";
        String component = "component2";
        ZonedDateTime startOfDay = getStartOfDay();
        ZonedDateTime oldestDay = startOfDay.minusDays(30);
        ZonedDateTime outOfRangeDay = startOfDay.minusDays(31);
        Reaction reaction = new Reaction(component, reactionId,
                new Observation("triggerType", "triggerFqn", Map.of()),
                new Observation("actionType", "actionFqn", Map.of()),
                startOfDay);
        reactionRepository.save(reaction);

        save(new ObservedReaction(component, reactionId, new Timeframe(outOfRangeDay, outOfRangeDay.plusHours(1)), 3));
        save(new ObservedReaction(component, reactionId, new Timeframe(oldestDay.plusHours(1), oldestDay.plusHours(2)), 5));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(10), startOfDay.plusHours(11)), 7));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(outOfRangeDay.toLocalDate());
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(oldestDay.toLocalDate());
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());

        // when
        LocalDate cutoffDate = startOfDay.minusDays(30).toLocalDate();
        observedReactionsAggregatedRepository.deleteAggregatedDataOlderThan(cutoffDate);

        // then: observed reactions older than 30 days are deleted
        List<Map<String, Object>> result = jdbcTemplate.queryForList("select date from observed_reactions_aggregated");
        result.forEach(map -> {
            java.sql.Date date = (java.sql.Date) map.get("date");
            assertThat(date.toLocalDate()).isAfterOrEqualTo(cutoffDate);
        });
    }

    @Test
    void aggregation_statistics_only_action() {
        String reactionId = "#actionId0";
        String reactionId1 = "#actionId1";
        String component = "component1";
        ZonedDateTime startOfDay = getStartOfDay();
        Reaction reaction = createReaction(component, reactionId, null, null, "actionType", "actionFqn");
        Reaction reaction1 = createReaction(component, reactionId1, null, null, "actionType1", "actionFqn1");
        reactionRepository.save(reaction);
        reactionRepository.save(reaction1);


        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay, startOfDay.plusHours(1)), 3));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(1), startOfDay.plusHours(2)), 5));
        save(new ObservedReaction(component, reactionId1, new Timeframe(startOfDay.plusHours(10), startOfDay.plusHours(11)), 7));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());

        List<ObservedReactionsAggregatedStatistics> statistics = observedReactionsAggregatedRepository.getStatistics(component, getToday().minusDays(1L));
        assertEquals(2, statistics.size());
        ObservedReactionsAggregatedStatistics statisticsEntry = statistics.getFirst();
        assertEquals("component1", statisticsEntry.component());
        assertEquals("actionType", statisticsEntry.actionType());
        assertEquals("actionFqn", statisticsEntry.actionFqn());
        assertEquals(8, statisticsEntry.count());
        assertEquals(8f, statisticsEntry.median());
        assertNull(statisticsEntry.percentage());

        statisticsEntry = statistics.getLast();
        assertEquals("component1", statisticsEntry.component());
        assertEquals("actionType1", statisticsEntry.actionType());
        assertEquals("actionFqn1", statisticsEntry.actionFqn());
        assertEquals(7, statisticsEntry.count());
        assertEquals(7f, statisticsEntry.median());
        assertNull(statisticsEntry.percentage());
    }

    @Test
    void aggregation_statistics_only_trigger() {
        String reactionId = "triggerId5";
        String reactionId1 = "triggerId6";
        String component = "component1";
        ZonedDateTime startOfDay = getStartOfDay();
        Reaction reaction = createReaction(component, reactionId, "triggerType", "triggerFqn", null, null);
        Reaction reaction1 = createReaction(component, reactionId1, "triggerType1", "triggerFqn1", null, null);
        reactionRepository.save(reaction);
        reactionRepository.save(reaction1);


        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay, startOfDay.plusHours(1)), 3));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(1), startOfDay.plusHours(2)), 5));
        save(new ObservedReaction(component, reactionId1, new Timeframe(startOfDay.plusHours(10), startOfDay.plusHours(11)), 7));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());

        List<ObservedReactionsAggregatedStatistics> statistics = observedReactionsAggregatedRepository.getStatistics(component, getToday().minusDays(1L));
        assertEquals(2, statistics.size());
        ObservedReactionsAggregatedStatistics statisticsEntry = statistics.getFirst();
        assertEquals("component1", statisticsEntry.component());
        assertEquals("triggerType", statisticsEntry.triggerType());
        assertEquals("triggerFqn", statisticsEntry.triggerFqn());
        assertEquals(8, statisticsEntry.count());
        assertEquals(8f, statisticsEntry.median());
        assertEquals(100.00, statisticsEntry.percentage());


        statisticsEntry = statistics.getLast();
        assertEquals("component1", statisticsEntry.component());
        assertEquals("triggerType1", statisticsEntry.triggerType());
        assertEquals("triggerFqn1", statisticsEntry.triggerFqn());
        assertEquals(7, statisticsEntry.count());
        assertEquals(7f, statisticsEntry.median());
        assertEquals(100.00, statisticsEntry.percentage());
    }

    private static Reaction createReaction(String component, String reactionId, String triggerType, String triggerFqn, String actionType, String actionFqn) {
        Map<String, String> triggerProps = triggerType != null ? Map.of("triggerKey1", "triggerValue1", "triggerKey2", "triggerValue2") : Map.of();
        Map<String, String> actionProps = actionType != null ? Map.of("actionKey1", "actionValue1", "actionKey2", "actionValue2") : Map.of();
        Observation trigger = new Observation(triggerType, triggerFqn, triggerProps);
        Observation action = new Observation(actionType, actionFqn, actionProps);
        return new Reaction(component, reactionId, trigger, action, ZonedDateTime.now());
    }

    private void save(ObservedReaction observedReaction) {
        observedReactionRepository.saveAll(randomUUID().toString(), List.of(observedReaction));
        entityManager.flush();
    }

}
