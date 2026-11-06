package ch.admin.bit.jeap.reaction.observer.domain;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReactionObserverMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ReactionObserverMetrics metrics = new ReactionObserverMetrics(meterRegistry);

    @Test
    void unknownReactionCountsSkipped_isRegisteredAtZero_beforeTheFirstSkip() {
        assertThat(meterRegistry.find(ReactionObserverMetrics.UNKNOWN_REACTION_COUNTS).counter().count()).isZero();
    }

    @Test
    void unknownReactionCountsSkipped_accumulates_andIgnoresNonPositiveValues() {
        metrics.unknownReactionCountsSkipped(3);
        metrics.unknownReactionCountsSkipped(2);
        metrics.unknownReactionCountsSkipped(0);

        assertThat(meterRegistry.find(ReactionObserverMetrics.UNKNOWN_REACTION_COUNTS).counter().count()).isEqualTo(5);
    }

    @Test
    void unparsableInterface_accumulatesOnTheSameCounter_perType_insteadOfRegisteringANewOneEveryTime() {
        metrics.unparsableInterface("event");
        metrics.unparsableInterface("EVENT");
        metrics.unparsableInterface("http_request");

        assertThat(meterRegistry.find(ReactionObserverMetrics.UNPARSABLE_INTERFACES).tag("type", "event")
                .counter().count()).isEqualTo(2);
        assertThat(meterRegistry.find(ReactionObserverMetrics.UNPARSABLE_INTERFACES).tag("type", "http_request")
                .counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find(ReactionObserverMetrics.UNPARSABLE_INTERFACES).counters()).hasSize(3);
    }

    @Test
    void unparsableInterface_isRegisteredAtZero_forEveryKnownType_beforeTheFirstOccurrence() {
        for (String type : new String[]{"event", "command", "http_request"}) {
            assertThat(meterRegistry.find(ReactionObserverMetrics.UNPARSABLE_INTERFACES).tag("type", type)
                    .counter().count()).isZero();
        }
    }
}
