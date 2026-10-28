package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionGraphBuilderService;
import ch.admin.bit.jeap.reaction.observer.domain.aggregation.AggregationService;
import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ScheduledTasksServiceTest {

    private static final int BATCH_SIZE = 1000;
    private static final int MAX_DELETIONS_PER_RUN = 500000;

    private final ObservedReactionRepository observedReactionRepository = mock(ObservedReactionRepository.class);
    private ScheduledTasksService scheduledTasksService;

    @BeforeEach
    void setUp() {
        // The scheduler's lock is not active in a unit test
        LockAssert.TestHelper.makeAllAssertsPass(true);
        ReactionObserverProperties properties = new ReactionObserverProperties();
        properties.setHousekeepingObservedReactionsBatchSize(BATCH_SIZE);
        properties.setHousekeepingObservedReactionsMaxDeletionsPerRun(MAX_DELETIONS_PER_RUN);
        scheduledTasksService = new ScheduledTasksService(mock(AggregationService.class), observedReactionRepository,
                properties, mock(ReactionGraphBuilderService.class), mock(GraphHolder.class));
    }

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    void cleanUpObservedReactions_deletesObservedReactionsBeforeTodayWithTheConfiguredLimits(CapturedOutput output) {
        when(observedReactionRepository.deleteByObservationDateBefore(any(), anyInt(), anyInt())).thenReturn(42L);

        scheduledTasksService.cleanUpObservedReactions();

        verify(observedReactionRepository).deleteByObservationDateBefore(getToday(), BATCH_SIZE, MAX_DELETIONS_PER_RUN);
        assertThat(output).contains("deleted 42 observed reactions")
                .doesNotContain("reached the per-run maximum");
    }

    @Test
    void cleanUpObservedReactions_warnsWhenThePerRunMaximumIsReached(CapturedOutput output) {
        when(observedReactionRepository.deleteByObservationDateBefore(any(), anyInt(), anyInt())).thenReturn((long) MAX_DELETIONS_PER_RUN);

        scheduledTasksService.cleanUpObservedReactions();

        assertThat(output).contains("reached the per-run maximum of 500000");
    }
}
