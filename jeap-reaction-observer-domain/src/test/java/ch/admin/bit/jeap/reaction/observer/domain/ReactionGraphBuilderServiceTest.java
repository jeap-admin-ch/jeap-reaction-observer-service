package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
    void buildGraph_handsTheMediansToTheGraphBeingBuilt() {
        Map<Long, Integer> medians = Map.of(200L, 42);
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
                .median(42)
                .build();

        Trigger trigger = Trigger.builder()
                .source(triggerInterface)
                .target(reaction)
                .median(42)
                .build();

        when(statisticsRepository.findReactionFksObservedSince(any())).thenReturn(Set.of(200L));
        when(statisticsRepository.getMedianPerReaction(any())).thenReturn(medians);
        // The medians go into the graph while it is built: node and edge have to hold the same reaction, or
        // the extractor no longer matches one to the other.
        when(graphRepository.buildFullGraph(medians))
                .thenReturn(new Graph(List.of(triggerInterface, reaction), List.of(trigger)));

        Graph graph = graphBuilderService.buildGraph(LocalDate.now());

        assertThat(graph.nodes()).filteredOn(Reaction.class::isInstance)
                .extracting(node -> ((Reaction) node).median())
                .containsExactly(42);
        assertThat(graph.edges()).filteredOn(Trigger.class::isInstance)
                .extracting(edge -> ((Trigger) edge).median())
                .containsExactly(42);
    }

    @Test
    void buildGraph_readsTheObservedReactionsBeforeTheMedians() {
        when(statisticsRepository.findReactionFksObservedSince(any())).thenReturn(Set.of());
        when(statisticsRepository.getMedianPerReaction(any())).thenReturn(Map.of());
        when(graphRepository.buildFullGraph(any())).thenReturn(new Graph(List.of(), List.of()));

        graphBuilderService.buildGraph(LocalDate.now());

        // An aggregation run landing between the two reads may only add a median, never leave a reaction of
        // the graph without one.
        InOrder inOrder = inOrder(statisticsRepository);
        inOrder.verify(statisticsRepository).findReactionFksObservedSince(any());
        inOrder.verify(statisticsRepository).getMedianPerReaction(any());
    }

    @Test
    void buildGraph_excludesReactionsNotObservedWithinPeriod() {
        // Setup: two reactions, each triggered by its own message
        Interface triggerInterfaceRecent = Message.builder()
                .id(100L).messageType("recentTrigger").variant("v1").semantic(SemanticType.EVENT).build();
        Interface triggerInterfaceOld = Message.builder()
                .id(101L).messageType("oldTrigger").variant("v1").semantic(SemanticType.EVENT).build();

        Reaction recentReaction = Reaction.builder().id(200L).component("comp").system("sys").median(42).build();
        Reaction oldReaction = Reaction.builder().id(201L).component("comp").system("sys").build();

        Trigger recentTrigger = Trigger.builder().source(triggerInterfaceRecent).target(recentReaction).median(42).build();
        Trigger oldTrigger = Trigger.builder().source(triggerInterfaceOld).target(oldReaction).build();

        Graph graph = new Graph(
                List.of(triggerInterfaceRecent, triggerInterfaceOld, recentReaction, oldReaction),
                List.of(recentTrigger, oldTrigger));

        when(graphRepository.buildFullGraph(any())).thenReturn(graph);
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

    @Test
    void buildGraph_whenTheRepositoryReturnsNothing_thenTheGraphIsEmpty() {
        when(statisticsRepository.findReactionFksObservedSince(any())).thenReturn(Set.of());
        when(statisticsRepository.getMedianPerReaction(any())).thenReturn(Map.of());
        when(graphRepository.buildFullGraph(any())).thenReturn(null);

        Graph graph = graphBuilderService.buildGraph(LocalDate.now());

        assertThat(graph.nodes()).isEmpty();
        assertThat(graph.edges()).isEmpty();
    }
}
