package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactionGraphBuilderServiceTest {

    @Mock
    private ReactionGraphRepository graphRepository;

    @Mock
    private ObservedReactionsAggregatedRepository statisticsRepository;

    private ReactionGraphBuilderService graphBuilderService;

    @BeforeEach
    void setUp() {
        graphBuilderService = new ReactionGraphBuilderService(graphRepository, statisticsRepository);
    }

    @Test
    void buildGraph_enrichesTriggerWithMedian() {
        // Setup: Dummy Interface and Reaction
        Interface triggerInterface = Message.builder()
                .id(100L)
                .messageType("trigger")
                .variant("v1")
                .semantic(SemanticType.EVENT)
                .build();

        Reaction reaction = Reaction.builder()
                .id(200L)
                .component("comp")
                .system("sys")
                .build();

        Trigger trigger = Trigger.builder()
                .source(triggerInterface)
                .target(reaction)
                .build();

        Graph graph = new Graph(List.of(triggerInterface, reaction), List.of(trigger));

        // Mock: Graph repository returns basic graph
        when(graphRepository.buildFullGraph()).thenReturn(graph);

        // Mock: Statistics repository returns median for reaction ID
        when(statisticsRepository.getMedianPerReaction(any())).thenReturn(Map.of(200L, 42));

        // Act
        Graph enrichedGraph = graphBuilderService.buildGraph(LocalDate.now());

        // Assert
        List<Trigger> enrichedTriggers = enrichedGraph.edges().stream()
                .filter(e -> e instanceof Trigger)
                .map(e -> (Trigger) e)
                .toList();

        assertThat(enrichedTriggers).hasSize(1);
        assertThat(enrichedTriggers.getFirst().median()).isEqualTo(42);
    }
}
