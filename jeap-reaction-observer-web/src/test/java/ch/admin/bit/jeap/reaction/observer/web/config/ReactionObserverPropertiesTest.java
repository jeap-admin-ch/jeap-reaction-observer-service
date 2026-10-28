package ch.admin.bit.jeap.reaction.observer.web.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactionObserverPropertiesTest {

    @Test
    void validateHousekeepingLimits_acceptsPositiveLimits() {
        assertDoesNotThrow(properties(1000, 500000)::validateHousekeepingLimits);
    }

    @Test
    void validateHousekeepingLimits_rejectsBatchSizeBelowOne() {
        assertThrows(IllegalStateException.class, properties(0, 500000)::validateHousekeepingLimits);
    }

    @Test
    void validateHousekeepingLimits_rejectsMaxDeletionsBelowOne() {
        assertThrows(IllegalStateException.class, properties(1000, 0)::validateHousekeepingLimits);
    }

    private static ReactionObserverProperties properties(int batchSize, int maxDeletionsPerRun) {
        ReactionObserverProperties properties = new ReactionObserverProperties();
        properties.setHousekeepingObservedReactionsBatchSize(batchSize);
        properties.setHousekeepingObservedReactionsMaxDeletionsPerRun(maxDeletionsPerRun);
        return properties;
    }
}
