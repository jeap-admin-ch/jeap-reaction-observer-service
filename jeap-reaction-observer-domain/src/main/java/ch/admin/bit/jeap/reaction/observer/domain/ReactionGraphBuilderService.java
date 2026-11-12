package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Component
@Slf4j
public class ReactionGraphBuilderService {

    private final ReactionGraphRepository graphRepository;
    private final ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;
    private final GraphExtractor graphExtractor;

    public Graph buildGraph(LocalDate fromDate) {
        // Only present reactions that have actually been observed within the statistics period.
        // Reactions are never deleted; they are merely hidden while not observed and reappear as soon as
        // they are observed again.
        // The reactions observed are those with a median.
        Map<Long, Integer> medians = observedReactionsAggregatedRepository.getMedianPerReaction(fromDate);

        // Build the full graph to get stable IDs, with the medians already in it
        Graph graph = graphRepository.buildFullGraph(medians);
        if (graph == null) {
            log.warn("No graph could be built from the repository. Returning empty graph.");
            return new Graph(List.of(), List.of());
        }

        return graphExtractor.getFilteredGraph(graph,
                reaction -> medians.containsKey(reaction.id()));
    }
}
