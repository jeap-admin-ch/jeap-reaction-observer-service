package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;

import java.util.Map;

public interface ReactionGraphRepository {

    /**
     * The whole graph, with every reaction node and every trigger edge carrying its median.
     * <p>
     * The medians are passed in rather than applied to a finished graph on purpose: nodes and edges hold the
     * same {@code Reaction} instances, and enriching only one of the two would leave records that no longer
     * compare equal - which is how {@code GraphExtractor} matches an edge to its node.
     *
     * @param medians the median of the daily observation counts per reaction id
     */
    Graph buildFullGraph(Map<Long, Integer> medians);
}
