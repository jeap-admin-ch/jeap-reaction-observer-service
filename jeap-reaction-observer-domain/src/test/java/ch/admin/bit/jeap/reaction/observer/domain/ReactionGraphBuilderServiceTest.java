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
import java.util.Set;

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
        // Use the real GraphExtractor so that the reaction filtering is exercised end-to-end.
        graphBuilderService = new ReactionGraphBuilderService(graphRepository, statisticsRepository, new GraphExtractor());
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

        // Mock: the reaction has been observed within the period
        when(statisticsRepository.findReactionFksObservedSince(any())).thenReturn(Set.of(200L));

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

    @Test
    void buildGraph_excludesReactionsNotObservedWithinPeriod() {
        // Setup: two reactions, each triggered by its own message
        Interface triggerInterfaceRecent = Message.builder()
                .id(100L).messageType("recentTrigger").variant("v1").semantic(SemanticType.EVENT).build();
        Interface triggerInterfaceOld = Message.builder()
                .id(101L).messageType("oldTrigger").variant("v1").semantic(SemanticType.EVENT).build();

        Reaction recentReaction = Reaction.builder().id(200L).component("comp").system("sys").build();
        Reaction oldReaction = Reaction.builder().id(201L).component("comp").system("sys").build();

        Trigger recentTrigger = Trigger.builder().source(triggerInterfaceRecent).target(recentReaction).build();
        Trigger oldTrigger = Trigger.builder().source(triggerInterfaceOld).target(oldReaction).build();

        Graph graph = new Graph(
                List.of(triggerInterfaceRecent, triggerInterfaceOld, recentReaction, oldReaction),
                List.of(recentTrigger, oldTrigger));

        when(graphRepository.buildFullGraph()).thenReturn(graph);
        // Only the recent reaction has been observed within the period - the old one must be filtered out.
        when(statisticsRepository.findReactionFksObservedSince(any())).thenReturn(Set.of(200L));
        when(statisticsRepository.getMedianPerReaction(any())).thenReturn(Map.of(200L, 42));

        // Act
        Graph result = graphBuilderService.buildGraph(LocalDate.now());

        // Assert: only the recently observed reaction (and its trigger message) remain
        assertThat(result.nodes()).containsExactlyInAnyOrder(recentReaction, triggerInterfaceRecent);
        assertThat(result.edges()).hasSize(1);
        assertThat(result.edges().getFirst()).isInstanceOf(Trigger.class);
        assertThat(((Trigger) result.edges().getFirst()).target()).isEqualTo(recentReaction);
    }
}
