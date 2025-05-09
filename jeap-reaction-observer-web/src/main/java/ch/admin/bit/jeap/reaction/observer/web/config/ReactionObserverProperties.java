package ch.admin.bit.jeap.reaction.observer.web.config;

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

}
