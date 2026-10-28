package ch.admin.bit.jeap.reaction.observer.web.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class ReactionObserverProperties {

    @Value("${jeap.reaction.observer.service.data-aggregation-cron-expression}")
    private String dataAggregationCronExpression;

    @Value("${jeap.reaction.observer.service.statistics-period-in-days}")
    private Long statisticsPeriodInDays;

    @Value("${jeap.reaction.observer.service.housekeeping-observed-reactions-cron-expression}")
    private String housekeepingObservedReactionsCronExpression;

    @Value("${jeap.reaction.observer.service.housekeeping-observed-reactions-batch-size}")
    private int housekeepingObservedReactionsBatchSize;

    @Value("${jeap.reaction.observer.service.housekeeping-observed-reactions-max-deletions-per-run}")
    private int housekeepingObservedReactionsMaxDeletionsPerRun;

    /**
     * Fails the startup on limits below 1, instead of failing the housekeeping every night.
     */
    @PostConstruct
    void validateHousekeepingLimits() {
        if (housekeepingObservedReactionsBatchSize < 1 || housekeepingObservedReactionsMaxDeletionsPerRun < 1) {
            throw new IllegalStateException("jeap.reaction.observer.service.housekeeping-observed-reactions-batch-size " +
                    "and housekeeping-observed-reactions-max-deletions-per-run must be at least 1, got %d and %d"
                            .formatted(housekeepingObservedReactionsBatchSize, housekeepingObservedReactionsMaxDeletionsPerRun));
        }
    }

    @Value("${jeap.reaction.observer.service.housekeeping-aggregated-data-cron-expression}")
    private String housekeepingAggregatedDataCronExpression;

}
