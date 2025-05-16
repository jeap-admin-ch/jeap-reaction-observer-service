package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.aggregation.AggregationService;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import io.micrometer.core.annotation.Timed;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getStartOfDay;
import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;

@AllArgsConstructor
@Component
public class ScheduledTasksService {

    private final AggregationService aggregationService;
    private final ObservedReactionRepository observedReactionRepository;
    private final ReactionObserverProperties properties;

    @Timed("reaction_observer_service_aggregate_data")
    @Scheduled(cron = "${jeap.reaction.observer.service.data-aggregation-cron-expression}")
    public void aggregateData() {
        LocalDate yesterday = getToday().minusDays(1L);
        this.aggregationService.aggregateData(yesterday);
    }

    @Scheduled(cron = "${jeap.reaction.observer.service.housekeeping-observed-reactions-cron-expression}")
    public void cleanUpObservedReactions() {
        this.observedReactionRepository.deleteByTimeframeStartBefore(getStartOfDay());
    }

    @Scheduled(cron = "${jeap.reaction.observer.service.housekeeping-aggregated-data-cron-expression}")
    public void cleanUpAggregatedData() {
        this.aggregationService.deleteAggregatedDataOlderThan(getToday().minusDays(properties.getStatisticsPeriodInDays()));
    }

}
