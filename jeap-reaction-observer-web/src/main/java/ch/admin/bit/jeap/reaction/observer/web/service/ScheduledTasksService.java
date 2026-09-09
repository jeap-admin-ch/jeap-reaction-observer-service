package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionGraphBuilderService;
import ch.admin.bit.jeap.reaction.observer.domain.aggregation.AggregationService;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getStartOfDay;
import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static java.time.LocalDate.now;

@Slf4j
@AllArgsConstructor
@Component
public class ScheduledTasksService {

    private final AggregationService aggregationService;
    private final ObservedReactionRepository observedReactionRepository;
    private final ReactionObserverProperties properties;
    private final ReactionGraphBuilderService graphBuilder;
    private final GraphHolder graphHolder;

    @PostConstruct
    public void init() {
        log.info("Initial graph refresh on startup (no lock)");
        refreshReactionGraphInternal();
    }

    /**
     * Rebuilds the graph this instance serves from - <b>on every instance, deliberately without a lock</b>.
     * <p>
     * The graph is held in memory per JVM ({@link GraphHolder}), and this task writes nothing: it reads the
     * reactions and the aggregated observations and replaces a field. A {@code @SchedulerLock} here would let
     * exactly one instance refresh and leave every other one serving the graph it built while it started,
     * which is a stale answer for as long as that instance lives - and, with entity tags derived from the
     * graph, an index from one instance and content from another that disagree. {@code @PostConstruct} already
     * refreshes unlocked on every instance for the same reason.
     * <p>
     * The locks stay on the three tasks below, which do write.
     */
    @Timed("reaction_observer_service_reaction_graph_refresh")
    @Scheduled(cron = "${jeap.reaction.observer.service.graph-refresh-cron-expression}")
    public void scheduledRefreshReactionGraph() {
        log.info("Starting scheduled reaction graph refresh");
        refreshReactionGraphInternal();
        log.info("Finished scheduled reaction graph refresh");
    }

    public void refreshReactionGraphInternal() {
        Graph graph = graphBuilder.buildGraph(LocalDate.now().minusDays(properties.getStatisticsPeriodInDays()));
        graphHolder.setGraph(graph);
    }

    @Timed("reaction_observer_service_aggregate_data")
    @SchedulerLock(name = "data-aggregation-task", lockAtLeastFor = "5s", lockAtMostFor = "2h")
    @Scheduled(cron = "${jeap.reaction.observer.service.data-aggregation-cron-expression}")
    public void aggregateData() {
        log.info("Starting scheduled data aggregation");
        LockAssert.assertLocked();
        LocalDate yesterday = getToday().minusDays(1L);
        this.aggregationService.aggregateData(yesterday);
        log.info("Finished scheduled data aggregation");
    }

    @SchedulerLock(name = "observedreactions-housekeeping-task", lockAtLeastFor = "5s", lockAtMostFor = "2h")
    @Scheduled(cron = "${jeap.reaction.observer.service.housekeeping-observed-reactions-cron-expression}")
    public void cleanUpObservedReactions() {
        log.info("Starting scheduled housekeeping for observed reactions");
        LockAssert.assertLocked();
        this.observedReactionRepository.deleteByTimeframeStartBefore(getStartOfDay());
        log.info("Finished scheduled housekeeping for observed reactions");
    }

    @SchedulerLock(name = "aggregated-data-housekeeping-task", lockAtLeastFor = "5s", lockAtMostFor = "2h")
    @Scheduled(cron = "${jeap.reaction.observer.service.housekeeping-aggregated-data-cron-expression}")
    public void cleanUpAggregatedData() {
        log.info("Starting scheduled housekeeping for aggregated data");
        LockAssert.assertLocked();
        this.aggregationService.deleteAggregatedDataOlderThan(getToday().minusDays(properties.getStatisticsPeriodInDays()));
        log.info("Finished scheduled housekeeping for aggregated data");
    }

}
