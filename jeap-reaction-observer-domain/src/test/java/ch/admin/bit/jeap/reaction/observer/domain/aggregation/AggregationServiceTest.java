package ch.admin.bit.jeap.reaction.observer.domain.aggregation;


import ch.admin.bit.jeap.reaction.observer.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

    ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository = mock(ObservedReactionsAggregatedRepository.class);

    AggregationService aggregationService = new AggregationService(observedReactionsAggregatedRepository);

    @Test
    void testAggregateDataStartOfDay() {
        // Arrange & Act
        aggregationService.aggregateData(getToday());

        // Assert
        verify(observedReactionsAggregatedRepository).aggregateObservedReactionsForDay(eq(getToday()));
    }

    @Test
    void testDeleteAggregatedDataOlderThan() {
        // Arrange & Act
        aggregationService.deleteAggregatedDataOlderThan(getToday());

        // Assert
        verify(observedReactionsAggregatedRepository).deleteAggregatedDataOlderThan(eq(getToday()));
    }

}