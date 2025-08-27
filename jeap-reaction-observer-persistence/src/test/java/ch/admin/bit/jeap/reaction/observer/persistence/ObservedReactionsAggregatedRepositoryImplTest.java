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
import java.util.List;
import java.util.Map;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getStartOfDay;
import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static java.util.Collections.*;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Slf4j
class ObservedReactionsAggregatedRepositoryImplTest {

    private static Map<String, String> actionProps = Map.of("actionKey1", "actionValue1", "actionKey2", "actionValue2");

    @Autowired
    private JpaReactionRepository jpaReactionRepository;

    @Autowired
    private JpaObservationPropertiesRepository jpaObservationPropertiesRepository;

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
        String reactionId = "r1";
        String component = "component1";
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        Map<String, String> triggerProps = Map.of("triggerKey1", "triggerValue1", "triggerKey2", "triggerValue2");
        Map<String, String> actionProps = Map.of("actionKey1", "actionValue1", "actionKey2", "actionValue2");
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", triggerProps),
                List.of(new Observation("a1", "actionType", "actionFqn", actionProps)),
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
        assertEquals("actionType", statisticsEntry.actions().getFirst().actionType());
        assertEquals("actionFqn", statisticsEntry.actions().getFirst().actionFqn());
        assertEquals(15, statisticsEntry.count());
        assertEquals(15f, statisticsEntry.median());
        assertEquals(100.00, statisticsEntry.percentage());

        // Verify trigger properties
        assertThat(statisticsEntry.triggerProperties()).isNotNull();
        assertEquals(2, statisticsEntry.triggerProperties().size());
        assertEquals("triggerValue1", statisticsEntry.triggerProperties().get("triggerKey1"));
        assertEquals("triggerValue2", statisticsEntry.triggerProperties().get("triggerKey2"));

        // Verify action properties
        assertThat(statisticsEntry.actions().getFirst().actionProperties()).isNotNull();
        assertEquals(2, statisticsEntry.actions().getFirst().actionProperties().size());
        assertEquals("actionValue1", statisticsEntry.actions().getFirst().actionProperties().get("actionKey1"));
        assertEquals("actionValue2", statisticsEntry.actions().getFirst().actionProperties().get("actionKey2"));
    }

    @Test
    void aggregation_statistics_multiple_actions() {
        String reactionId = "r3";
        String component = "component1";
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        Map<String, String> triggerProps = Map.of("triggerKey1", "triggerValue1", "triggerKey2", "triggerValue2");
        Map<String, String> actionProps = Map.of("actionKey1", "actionValue1", "actionKey2", "actionValue2");
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", triggerProps),
                List.of(new Observation("a1", "actionType", "actionFqn", actionProps),
                        new Observation("a2", "actionType1", "actionFqn1", emptyMap())),
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
        assertEquals(15, statisticsEntry.count());
        assertEquals(15f, statisticsEntry.median());
        assertEquals(100.00, statisticsEntry.percentage());

        // Verify trigger properties
        assertThat(statisticsEntry.triggerProperties()).isNotNull();
        assertEquals(2, statisticsEntry.triggerProperties().size());
        assertEquals("triggerValue1", statisticsEntry.triggerProperties().get("triggerKey1"));
        assertEquals("triggerValue2", statisticsEntry.triggerProperties().get("triggerKey2"));

        // Verify actions
        Action firstAction = statisticsEntry.actions().getFirst();
        assertEquals("actionType", firstAction.actionType());
        assertEquals("actionFqn", firstAction.actionFqn());
        assertThat(firstAction.actionProperties()).isNotNull();
        assertEquals(2, firstAction.actionProperties().size());
        assertEquals("actionValue1", firstAction.actionProperties().get("actionKey1"));
        assertEquals("actionValue2", firstAction.actionProperties().get("actionKey2"));

        Action secondAction = statisticsEntry.actions().getLast();
        assertEquals("actionType1", secondAction.actionType());
        assertEquals("actionFqn1", secondAction.actionFqn());
        assertThat(secondAction.actionProperties()).isEqualTo(emptyMap());
    }

    @Test
    void aggregation_statistics_multiple_reactions_same_trigger() {
        String reactionId = "r6";
        String reactionId1 = "r7";
        String reactionId2 = "r8";
        String component = "component1";
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", emptyMap()),
                List.of(new Observation("a1", "actionType", "actionFqn", emptyMap())),
                startOfDay);
        Reaction reaction1 = new Reaction(system, component, reactionId1,
                new Observation("t1", "triggerType", "triggerFqn", emptyMap()),
                List.of(new Observation("a2", "actionType1", "actionFqn1", emptyMap())),
                startOfDay);
        Reaction reaction2 = new Reaction(system, component, reactionId2,
                new Observation("t1", "triggerType", "triggerFqn", emptyMap()),
                List.of(new Observation("a1", "actionType", "actionFqn", emptyMap()),
                        new Observation("a2", "actionType1", "actionFqn1", emptyMap())),
                startOfDay);
        reactionRepository.save(reaction);
        reactionRepository.save(reaction1);
        reactionRepository.save(reaction2);

        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay, startOfDay.plusHours(1)), 10));
        save(new ObservedReaction(component, reactionId1, new Timeframe(startOfDay.plusHours(1), startOfDay.plusHours(2)), 10));
        save(new ObservedReaction(component, reactionId2, new Timeframe(startOfDay.plusHours(10), startOfDay.plusHours(11)), 20));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());

        List<ObservedReactionsAggregatedStatistics> statistics = observedReactionsAggregatedRepository.getStatistics(component, getToday().minusDays(1L));
        assertEquals(3, statistics.size());

        ObservedReactionsAggregatedStatistics statisticsEntry = statistics.getFirst();

        assertEquals("component1", statisticsEntry.component());
        assertEquals("triggerType", statisticsEntry.triggerType());
        assertEquals("triggerFqn", statisticsEntry.triggerFqn());
        assertEquals(20, statisticsEntry.count());
        assertEquals(20, statisticsEntry.median());
        assertEquals(50.00, statisticsEntry.percentage());

        statisticsEntry = statistics.get(1);
        assertEquals("component1", statisticsEntry.component());
        assertEquals("triggerType", statisticsEntry.triggerType());
        assertEquals("triggerFqn", statisticsEntry.triggerFqn());
        assertEquals(10, statisticsEntry.count());
        assertEquals(10f, statisticsEntry.median());
        assertEquals(25.00, statisticsEntry.percentage());

        statisticsEntry = statistics.getLast();
        assertEquals("component1", statisticsEntry.component());
        assertEquals("triggerType", statisticsEntry.triggerType());
        assertEquals("triggerFqn", statisticsEntry.triggerFqn());
        assertEquals(10, statisticsEntry.count());
        assertEquals(10f, statisticsEntry.median());
        assertEquals(25.00, statisticsEntry.percentage());
    }

    @Test
    void aggregation_statistics_multiple_actions_both_have_properties() {
        String reactionId = "r4";
        String component = "component1";
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        Map<String, String> triggerProps = Map.of("triggerKey1", "triggerValue1", "triggerKey2", "triggerValue2");
        Map<String, String> actionProps = Map.of("actionKey1", "actionValue1", "actionKey2", "actionValue2");
        Map<String, String> actionProps1 = Map.of("actionKey3", "actionValue3");
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", triggerProps),
                List.of(new Observation("a1", "actionType", "actionFqn", actionProps),
                        new Observation("a2", "actionType1", "actionFqn1", actionProps1)),
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
        assertEquals(15, statisticsEntry.count());
        assertEquals(15f, statisticsEntry.median());
        assertEquals(100.00, statisticsEntry.percentage());

        // Verify trigger properties
        assertThat(statisticsEntry.triggerProperties()).isNotNull();
        assertEquals(2, statisticsEntry.triggerProperties().size());
        assertEquals("triggerValue1", statisticsEntry.triggerProperties().get("triggerKey1"));
        assertEquals("triggerValue2", statisticsEntry.triggerProperties().get("triggerKey2"));

        // Verify actions
        Action firstAction = statisticsEntry.actions().getFirst();
        assertEquals("actionType", firstAction.actionType());
        assertEquals("actionFqn", firstAction.actionFqn());
        assertThat(firstAction.actionProperties()).isNotNull();
        assertEquals(2, firstAction.actionProperties().size());
        assertEquals("actionValue1", firstAction.actionProperties().get("actionKey1"));
        assertEquals("actionValue2", firstAction.actionProperties().get("actionKey2"));

        Action secondAction = statisticsEntry.actions().getLast();
        assertEquals("actionType1", secondAction.actionType());
        assertEquals("actionFqn1", secondAction.actionFqn());
        assertEquals(1, secondAction.actionProperties().size());
        assertEquals("actionValue3", secondAction.actionProperties().get("actionKey3"));
    }

    @Test
    void aggregation_statistics_multiple_actions_none_have_properties() {
        String reactionId = "r5";
        String component = "component1";
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        Map<String, String> triggerProps = Map.of("triggerKey1", "triggerValue1", "triggerKey2", "triggerValue2");
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", triggerProps),
                List.of(new Observation("a1", "actionType", "actionFqn", emptyMap()),
                        new Observation("a2", "actionType1", "actionFqn1", emptyMap())),
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
        assertEquals(15, statisticsEntry.count());
        assertEquals(15f, statisticsEntry.median());
        assertEquals(100.00, statisticsEntry.percentage());

        // Verify trigger properties
        assertThat(statisticsEntry.triggerProperties()).isNotNull();
        assertEquals(2, statisticsEntry.triggerProperties().size());
        assertEquals("triggerValue1", statisticsEntry.triggerProperties().get("triggerKey1"));
        assertEquals("triggerValue2", statisticsEntry.triggerProperties().get("triggerKey2"));

        // Verify actions
        Action firstAction = statisticsEntry.actions().getFirst();
        assertEquals("actionType", firstAction.actionType());
        assertEquals("actionFqn", firstAction.actionFqn());
        assertThat(firstAction.actionProperties()).isEqualTo(emptyMap());

        Action secondAction = statisticsEntry.actions().getLast();
        assertEquals("actionType1", secondAction.actionType());
        assertEquals("actionFqn1", secondAction.actionFqn());
        assertThat(secondAction.actionProperties()).isEqualTo(emptyMap());
    }

    @Test
    void aggregation_statistics_different_days() {
        String reactionId = "triggerId1#actionId1";
        String component = "component2";
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        ZonedDateTime yesterday = startOfDay.minusDays(1);
        ZonedDateTime theDayBefore = yesterday.minusDays(1);
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())),
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
        assertEquals(emptyMap(), statisticsEntry.actions().getFirst().actionProperties());
        assertEquals(emptyMap(), statisticsEntry.triggerProperties());
        assertEquals("actionType", statisticsEntry.actions().getFirst().actionType());
        assertEquals("actionFqn", statisticsEntry.actions().getFirst().actionFqn());
        assertEquals(15, statisticsEntry.count());
        assertEquals(5f, statisticsEntry.median());
        assertEquals(100.00, statisticsEntry.percentage());
    }

    @Test
    void aggregation_statistics_two_different_actions_same_trigger() {
        String component = "component1";
        String system = "system1";
        Reaction reaction = createReaction(system, component, "triggerId2#actionId0", "t1", "triggerType", "triggerFqn", singletonList(new Observation("a1", "actionType", "actionFqn", actionProps)));
        Reaction reaction1 = createReaction(system, component, "triggerId2#actionId1", "t1", "triggerType", "triggerFqn", singletonList(new Observation("a1", "actionType1", "actionFqn1", actionProps)));
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
        assertEquals(20L, statisticsFirstReaction.count());
        assertEquals(20f, statisticsFirstReaction.median());
        assertEquals(20.00, statisticsFirstReaction.percentage());

        ObservedReactionsAggregatedStatistics statisticsSecondReaction = statistics.getLast();
        assertEquals(80L, statisticsSecondReaction.count());
        assertEquals(10f, statisticsSecondReaction.median());
        assertEquals(80.00, statisticsSecondReaction.percentage());
    }

    @Test
    void aggregation_statistics_same_reactions_different_components() {
        String reactionId = "triggerId3#actionId0";
        String system = "system1";
        Reaction reaction = createReaction(system, "component1", reactionId, "t1", "triggerType", "triggerFqn", singletonList(new Observation("a1", "actionType", "actionFqn", actionProps)));
        Reaction reaction1 = createReaction(system, "component2", reactionId, "t1", "triggerType", "triggerFqn", singletonList(new Observation("a1", "actionType", "actionFqn", actionProps)));
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
    void delete_aggregated_data() {
        // given: a reaction and several observations over different periods
        String reactionId = "reactionId4";
        String component = "component2";
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        ZonedDateTime oldestDay = startOfDay.minusDays(30);
        ZonedDateTime outOfRangeDay = startOfDay.minusDays(31);
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())),
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
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        Reaction reaction = createReaction(system, component, reactionId, "t1", null, null, singletonList(new Observation("a1", "actionType", "actionFqn", actionProps)));
        Reaction reaction1 = createReaction(system, component, reactionId1, "t1", null, null, singletonList(new Observation("a1", "actionType1", "actionFqn1", actionProps)));
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
        assertEquals("actionType1", statisticsEntry.actions().getFirst().actionType());
        assertEquals("actionFqn1", statisticsEntry.actions().getFirst().actionFqn());
        assertEquals(7, statisticsEntry.count());
        assertEquals(7f, statisticsEntry.median());
        assertNull(statisticsEntry.percentage());

        statisticsEntry = statistics.getLast();
        assertEquals("component1", statisticsEntry.component());
        assertEquals("actionType", statisticsEntry.actions().getFirst().actionType());
        assertEquals("actionFqn", statisticsEntry.actions().getFirst().actionFqn());
        assertEquals(8, statisticsEntry.count());
        assertEquals(8f, statisticsEntry.median());
        assertNull(statisticsEntry.percentage());
    }

    @Test
    void aggregation_statistics_only_trigger() {
        String reactionId = "triggerId5";
        String reactionId1 = "triggerId6";
        String component = "component1";
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        Reaction reaction = createReaction(system, component, reactionId, "t1", "triggerType", "triggerFqn", emptyList());
        Reaction reaction1 = createReaction(system, component, reactionId1, "t2", "triggerType1", "triggerFqn1", emptyList());
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
        assertEquals("triggerType1", statisticsEntry.triggerType());
        assertEquals("triggerFqn1", statisticsEntry.triggerFqn());
        assertEquals(7, statisticsEntry.count());
        assertEquals(7f, statisticsEntry.median());
        assertEquals(100.00, statisticsEntry.percentage());


        statisticsEntry = statistics.getLast();
        assertEquals("component1", statisticsEntry.component());
        assertEquals("triggerType", statisticsEntry.triggerType());
        assertEquals("triggerFqn", statisticsEntry.triggerFqn());
        assertEquals(8, statisticsEntry.count());
        assertEquals(8f, statisticsEntry.median());
        assertEquals(100.00, statisticsEntry.percentage());
    }

    @Test
    void statistics_not_found() {
        String reactionId = "triggerId5#actionId5";
        String component = "component1";
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", emptyMap()),
                List.of(new Observation("a1", "actionType", "actionFqn", emptyMap())),
                startOfDay);
        reactionRepository.save(reaction);

        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay, startOfDay.plusHours(1)), 3));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(1), startOfDay.plusHours(2)), 5));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(10), startOfDay.plusHours(11)), 7));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());

        List<ObservedReactionsAggregatedStatistics> statistics = observedReactionsAggregatedRepository.getStatistics("unknown", getToday().minusDays(1L));
        assertEquals(0, statistics.size());
    }

    private static Reaction createReaction(String system, String component, String reactionId, String triggerId, String triggerType, String triggerFqn, List<Observation> actions) {
        Map<String, String> triggerProps = triggerType != null ? Map.of("triggerKey1", "triggerValue1", "triggerKey2", "triggerValue2") : Map.of();
        Observation trigger = new Observation(triggerId, triggerType, triggerFqn, triggerProps);
        return new Reaction(system, component, reactionId, trigger, actions, ZonedDateTime.now());
    }

    private void save(ObservedReaction observedReaction) {
        observedReactionRepository.saveAll(randomUUID().toString(), List.of(observedReaction));
        entityManager.flush();
    }

}
