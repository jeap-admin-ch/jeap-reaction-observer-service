package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.*;
import ch.admin.bit.jeap.reaction.observer.domain.models.*;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getStartOfDay;
import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static java.util.Collections.*;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Slf4j
class ObservedReactionsAggregatedRepositoryImplTest {

    private static final Map<String, String> actionProps = Map.of("actionKey1", "actionValue1", "actionKey2", "actionValue2");

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
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", triggerProps),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of("actionKey1", "actionValue1", "actionKey2", "actionValue2"))),
                startOfDay);
        reactionRepository.save(reaction);

        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay, startOfDay.plusHours(1)), 3));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(1), startOfDay.plusHours(2)), 5));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(10), startOfDay.plusHours(11)), 7));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());

        Map<Long, Integer> medianPerReaction = observedReactionsAggregatedRepository.getMedianPerReaction(getToday().minusDays(1L));
        assertEquals(1, medianPerReaction.size());
        assertTrue(medianPerReaction.containsValue(15));
    }

    @Test
    void aggregation_statistics_multiple_actions() {
        String reactionId = "r3";
        String component = "component1";
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        Map<String, String> triggerProps = Map.of("triggerKey1", "triggerValue1", "triggerKey2", "triggerValue2");
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", triggerProps),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of("actionKey1", "actionValue1", "actionKey2", "actionValue2")),
                        new Observation("a2", "actionType1", "actionFqn1", emptyMap())),
                startOfDay);
        reactionRepository.save(reaction);

        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay, startOfDay.plusHours(1)), 3));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(1), startOfDay.plusHours(2)), 5));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(10), startOfDay.plusHours(11)), 7));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());

        Map<Long, Integer> medianPerReaction = observedReactionsAggregatedRepository.getMedianPerReaction(getToday().minusDays(1L));
        assertEquals(1, medianPerReaction.size());
        assertTrue(medianPerReaction.containsValue(15));
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

        Map<Long, Integer> medianPerReaction = observedReactionsAggregatedRepository.getMedianPerReaction(getToday().minusDays(1L));
        assertEquals(3, medianPerReaction.size());
        assertTrue(medianPerReaction.containsValue(10));
        assertTrue(medianPerReaction.containsValue(20));
    }

    @Test
    void aggregation_statistics_multiple_actions_both_have_properties() {
        String reactionId = "r4";
        String component = "component1";
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        Map<String, String> triggerProps = Map.of("triggerKey1", "triggerValue1", "triggerKey2", "triggerValue2");
        Map<String, String> actionProps1 = Map.of("actionKey3", "actionValue3");
        Reaction reaction = new Reaction(system, component, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", triggerProps),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of("actionKey1", "actionValue1", "actionKey2", "actionValue2")),
                        new Observation("a2", "actionType1", "actionFqn1", actionProps1)),
                startOfDay);
        reactionRepository.save(reaction);

        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay, startOfDay.plusHours(1)), 3));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(1), startOfDay.plusHours(2)), 5));
        save(new ObservedReaction(component, reactionId, new Timeframe(startOfDay.plusHours(10), startOfDay.plusHours(11)), 7));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());

        Map<Long, Integer> medianPerReaction = observedReactionsAggregatedRepository.getMedianPerReaction(getToday().minusDays(1L));
        assertEquals(1, medianPerReaction.size());
        assertTrue(medianPerReaction.containsValue(15));
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

        Map<Long, Integer> medianPerReaction = observedReactionsAggregatedRepository.getMedianPerReaction(getToday().minusDays(1L));
        assertEquals(1, medianPerReaction.size());
        assertTrue(medianPerReaction.containsValue(15));
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


        Map<Long, Integer> medianPerReaction = observedReactionsAggregatedRepository.getMedianPerReaction(getToday().minusDays(1L));
        assertEquals(1, medianPerReaction.size());
        assertTrue(medianPerReaction.containsValue(6));
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

        Map<Long, Integer> medianPerReaction = observedReactionsAggregatedRepository.getMedianPerReaction(getToday().minusDays(1L));
        assertEquals(2, medianPerReaction.size());
        assertTrue(medianPerReaction.containsValue(10));
        assertTrue(medianPerReaction.containsValue(20));
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

        Map<Long, Integer> medianPerReaction = observedReactionsAggregatedRepository.getMedianPerReaction(getToday().minusDays(1L));
        assertEquals(2, medianPerReaction.size());
        assertTrue(medianPerReaction.containsValue(8));
        assertTrue(medianPerReaction.containsValue(7));
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

        Map<Long, Integer> medianPerReaction = observedReactionsAggregatedRepository.getMedianPerReaction(getToday().minusDays(1L));
        assertEquals(2, medianPerReaction.size());
        assertTrue(medianPerReaction.containsValue(8));
        assertTrue(medianPerReaction.containsValue(7));
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

    @Test
    void getMedianPerReaction_returnsCorrectMedian() {
        Reaction reaction = new Reaction("system1", "component1", "rMedian",
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())),
                getStartOfDay());
        reactionRepository.save(reaction);

        save(new ObservedReaction("component1", "rMedian", new Timeframe(getStartOfDay(), getStartOfDay().plusHours(1)), 7));
        save(new ObservedReaction("component1", "rMedian", new Timeframe(getStartOfDay().minusDays(1), getStartOfDay().minusDays(1).plusHours(1)), 5));
        save(new ObservedReaction("component1", "rMedian", new Timeframe(getStartOfDay().minusDays(2), getStartOfDay().minusDays(2).plusHours(1)), 3));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday().minusDays(0));
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday().minusDays(1));
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday().minusDays(2));

        Map<Long, Integer> medians = observedReactionsAggregatedRepository.getMedianPerReaction(getToday().minusDays(3));

        // Assert
        assertEquals(1, medians.size(), "Expected one entry in the medians map");
        Integer median = medians.values().iterator().next();
        assertEquals(5, median); // Median from [3, 5, 7]
    }

    @Test
    void getMedianPerReaction_returnsEmptyMapWhenNoData() {
        Map<Long, Integer> medians = observedReactionsAggregatedRepository.getMedianPerReaction(getToday().minusDays(1));
        assertTrue(medians.isEmpty(), "Expected empty map when no data is present");
    }

    @Test
    void getLastObservedReactionDatePerComponent() {
        String reactionId = "triggerId1#actionId1";
        String reactionId2 = "triggerId1#actionId2";
        String component2 = "component2";
        String component3 = "component3";
        String system = "system1";
        ZonedDateTime startOfDay = getStartOfDay();
        ZonedDateTime yesterday = startOfDay.minusDays(1);
        ZonedDateTime theDayBefore = yesterday.minusDays(1);
        Reaction reaction1 = new Reaction(system, component2, reactionId,
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())),
                startOfDay);
        reactionRepository.save(reaction1);
        Reaction reaction2 = new Reaction(system, component3, reactionId2,
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())),
                startOfDay);
        reactionRepository.save(reaction2);

        save(new ObservedReaction(component2, reactionId, new Timeframe(theDayBefore, theDayBefore.plusHours(1)), 3));
        save(new ObservedReaction(component2, reactionId, new Timeframe(yesterday.plusHours(1), yesterday.plusHours(2)), 5));
        save(new ObservedReaction(component2, reactionId, new Timeframe(startOfDay.plusHours(10), startOfDay.plusHours(11)), 7));
        save(new ObservedReaction(component3, reactionId2, new Timeframe(theDayBefore, theDayBefore.plusHours(1)), 7));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(theDayBefore.toLocalDate());
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(yesterday.toLocalDate());
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());

        Map<String, LocalDate> aggregatedData = observedReactionsAggregatedRepository.getLastObservedReactionDatePerComponent();
        assertEquals(2, aggregatedData.size());
        assertEquals(LocalDate.now(), aggregatedData.get(component2));
        assertEquals(LocalDate.now().minusDays(2), aggregatedData.get(component3));
    }

    @Test
    void findReactionFksObservedSince() {
        String system = "system1";
        String component = "component1";
        ZonedDateTime startOfDay = getStartOfDay();
        ZonedDateTime longAgo = startOfDay.minusDays(40);

        // recentReaction: observed today
        Reaction recentReaction = new Reaction(system, component, "recent",
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())), startOfDay);
        reactionRepository.save(recentReaction);
        save(new ObservedReaction(component, "recent", new Timeframe(startOfDay, startOfDay.plusHours(1)), 5));

        // zeroCountReaction: observed today but with a count of zero - must still be considered observed
        Reaction zeroCountReaction = new Reaction(system, component, "zero",
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())), startOfDay);
        reactionRepository.save(zeroCountReaction);
        save(new ObservedReaction(component, "zero", new Timeframe(startOfDay, startOfDay.plusHours(1)), 0));

        // oldReaction: only observed 40 days ago, before the window
        Reaction oldReaction = new Reaction(system, component, "old",
                new Observation("t1", "triggerType", "triggerFqn", Map.of()),
                List.of(new Observation("a1", "actionType", "actionFqn", Map.of())), longAgo);
        reactionRepository.save(oldReaction);
        save(new ObservedReaction(component, "old", new Timeframe(longAgo, longAgo.plusHours(1)), 9));

        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(getToday());
        observedReactionsAggregatedRepository.aggregateObservedReactionsForDay(longAgo.toLocalDate());

        Long recentFk = jpaReactionRepository.findIdByComponentAndReactionId(component, "recent").orElseThrow();
        Long zeroFk = jpaReactionRepository.findIdByComponentAndReactionId(component, "zero").orElseThrow();
        Long oldFk = jpaReactionRepository.findIdByComponentAndReactionId(component, "old").orElseThrow();

        Set<Long> observedSince = observedReactionsAggregatedRepository.findReactionFksObservedSince(getToday().minusDays(32));

        assertTrue(observedSince.contains(recentFk), "recently observed reaction must be included");
        assertTrue(observedSince.contains(zeroFk), "reaction observed with count 0 must still be included");
        assertFalse(observedSince.contains(oldFk), "reaction observed only before the window must be excluded");
        assertEquals(2, observedSince.size());
    }
}
