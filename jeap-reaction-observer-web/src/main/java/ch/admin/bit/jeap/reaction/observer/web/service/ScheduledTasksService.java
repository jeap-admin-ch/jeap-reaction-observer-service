package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.aggregation.AggregationService;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import io.micrometer.core.annotation.Timed;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getStartOfDay;
import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;

@Slf4j
@AllArgsConstructor
@Component
public class ScheduledTasksService {

    private final AggregationService aggregationService;
    private final ObservedReactionRepository observedReactionRepository;
    private final ReactionObserverProperties properties;

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
