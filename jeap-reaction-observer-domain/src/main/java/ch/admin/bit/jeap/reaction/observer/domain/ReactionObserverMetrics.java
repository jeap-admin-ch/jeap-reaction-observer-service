package ch.admin.bit.jeap.reaction.observer.domain;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The counters of what the observer accepts without being able to use it fully.
 */
public class ReactionObserverMetrics {

    static final String UNKNOWN_REACTION_COUNTS = "reaction_observer_service_unknown_reaction_counts";
    static final String UNPARSABLE_INTERFACES = "reaction_observer_service_unparsable_interfaces";

    private static final List<String> COUNTED_INTERFACE_TYPES = List.of(
            InterfaceNodeFactory.TYPE_EVENT, InterfaceNodeFactory.TYPE_COMMAND, InterfaceNodeFactory.TYPE_HTTP_REQUEST);

    private final Counter unknownReactionCounts;
    private final Map<String, Counter> unparsableInterfaceCounters;

    public ReactionObserverMetrics(MeterRegistry meterRegistry) {
        // Registered up front, so that the counter is exported as 0 before the first count is skipped
        this.unknownReactionCounts = Counter.builder(UNKNOWN_REACTION_COUNTS)
                .description("Observed reaction counts skipped because their reaction has not been identified")
                .register(meterRegistry);
        // Also registered up front: a series created by its first increment would make increase() and rate() read
        // that increment as 0, and each interface is counted only once per instance
        this.unparsableInterfaceCounters = COUNTED_INTERFACE_TYPES.stream()
                .collect(Collectors.toUnmodifiableMap(type -> type, type -> registerUnparsableInterfaceCounter(meterRegistry, type)));
    }

    /** Counts observed reaction counts that were skipped because their reaction id is not known. */
    public void unknownReactionCountsSkipped(int skipped) {
        if (skipped > 0) {
            unknownReactionCounts.increment(skipped);
        }
    }

    /**
     * Counts an interface of a known observation type whose FQN could not be parsed.
     *
     * @param type the observation type, one of the known types (event, command, http_request)
     */
    public void unparsableInterface(String type) {
        Counter counter = unparsableInterfaceCounters.get(type.toLowerCase(Locale.ROOT));
        if (counter == null) {
            throw new IllegalArgumentException("Not a known observation type: " + type);
        }
        counter.increment();
    }

    private static Counter registerUnparsableInterfaceCounter(MeterRegistry meterRegistry, String type) {
        return Counter.builder(UNPARSABLE_INTERFACES)
                .description("Interfaces of a known observation type whose FQN could not be parsed")
                .tag("type", type)
                .register(meterRegistry);
    }
}
