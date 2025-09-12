package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@AllArgsConstructor
@Component
@Slf4j
public class ReactionGraphBuilderService {

    private final ReactionGraphRepository graphRepository;
    private final ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;


    public Graph buildGraph(LocalDate fromDate) {
        Graph graph = graphRepository.buildFullGraph();
        if (graph == null) {
            log.warn("No graph could be built from the repository. Returning empty graph.");
            return new Graph(List.of(), List.of());
        }

        Map<Long, Integer> medians = observedReactionsAggregatedRepository.getMedianPerReaction(fromDate);

        // Enrich Trigger edges with median values
        List<Edge> enrichedEdges = graph.edges().stream()
                .map(edge -> {
                    if (edge instanceof Trigger trigger) {
                        Long reactionId = trigger.target().getId();
                        Integer median = medians.getOrDefault(reactionId, null);
                        return Trigger.builder()
                                .source(trigger.source())
                                .target(trigger.target())
                                .median(median)
                                .build();
                    }
                    return edge;
                })
                .toList();

        return new Graph(graph.nodes(), enrichedEdges);
    }
}
