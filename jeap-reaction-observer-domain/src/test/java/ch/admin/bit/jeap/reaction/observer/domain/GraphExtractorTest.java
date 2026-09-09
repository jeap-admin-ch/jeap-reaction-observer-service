package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import java.util.Set;

class GraphExtractorTest {

    private final GraphExtractor extractor = new GraphExtractor();
    // [M1] --trigger1--> [R1] --action1--> [M2] --unrelatedTrigger--> [R2]
    private final Message message1 = Message.builder()
            .id(1L)
            .messageType("TypeA")
            .variant("v1")
            .semantic(SemanticType.EVENT)
            .build();

    private final Message message2 = Message.builder()
            .id(2L)
            .messageType("TypeB")
            .variant("v1")
            .semantic(SemanticType.COMMAND)
            .build();

    private final Reaction reaction1 = Reaction.builder()
            .id(100L)
            .component("ComponentX")
            .system("SystemA")
            .build();

    private final Reaction reaction2 = Reaction.builder()
            .id(200L)
            .component("ComponentY")
            .system("SystemB")
            .build();

    private final Trigger trigger1 = Trigger.builder()
            .source(message1)
            .target(reaction1)
            .median(5)
            .build();

    private final Action action1 = Action.builder()
            .source(reaction1)
            .target(message2)
            .build();

    private final Trigger unrelatedTrigger = Trigger.builder()
            .source(message2)
            .target(reaction2)
            .median(3)
            .build();

    private final Graph fullGraph = new Graph(
            List.of(message1, message2, reaction1, reaction2),
            List.of(trigger1, action1, unrelatedTrigger)
    );

    @Test
    void testGetSystemRelatedGraph() {
        Graph result = extractor.getSystemRelatedGraph(fullGraph, "SYSTEMA");

        assertEquals(3, result.nodes().size(), "Should contain 1 reaction and 2 messages");
        assertTrue(result.nodes().contains(reaction1));
        assertTrue(result.nodes().contains(message1));
        assertTrue(result.nodes().contains(message2));

        assertEquals(2, result.edges().size(), "Should contain 2 edges (trigger1 and action1)");
        assertTrue(result.edges().contains(trigger1));
        assertTrue(result.edges().contains(action1));

        assertFalse(result.nodes().contains(reaction2));
        assertFalse(result.edges().contains(unrelatedTrigger));
    }

    @Test
    void testGetComponentRelatedGraph() {
        Graph result = extractor.getComponentRelatedGraph(fullGraph, "ComponentX");

        assertEquals(3, result.nodes().size(), "Should contain 1 reaction and 2 messages");
        assertTrue(result.nodes().contains(reaction1));
        assertTrue(result.nodes().contains(message1));
        assertTrue(result.nodes().contains(message2));

        assertEquals(2, result.edges().size());
        assertTrue(result.edges().contains(trigger1));
        assertTrue(result.edges().contains(action1));
    }

    @Test
    void testGetMessageRelatedGraph() {
        Graph result = extractor.getMessageRelatedGraph(fullGraph, "TypeA", "v1");

        assertEquals(3, result.nodes().size(), "Should contain message1 , message2 and reaction1");
        assertTrue(result.nodes().contains(message1));
        assertTrue(result.nodes().contains(message2));
        assertTrue(result.nodes().contains(reaction1));

        assertEquals(2, result.edges().size());
        assertTrue(result.edges().contains(trigger1));
        assertTrue(result.edges().contains(action1));
    }

    @Test
    void testGetMessageRelatedGraph_followUpFromMessage2() {
        Graph result = extractor.getMessageRelatedGraph(fullGraph, "TypeB", "v1");

        // Expected: message2, reaction1, message1, reaction2
        assertEquals(4, result.nodes().size(), "Should contain message2, reaction1, message1, and reaction2");
        assertTrue(result.nodes().contains(message2));
        assertTrue(result.nodes().contains(reaction1));
        assertTrue(result.nodes().contains(message1));

        assertTrue(result.nodes().contains(reaction2));

        assertEquals(3, result.edges().size(), "Should contain action1, trigger1, and unrelatedTrigger");
        assertTrue(result.edges().contains(action1));
        assertTrue(result.edges().contains(trigger1));
        assertTrue(result.edges().contains(unrelatedTrigger));
    }

    @Test
    void testGetSystemRelatedGraph_emptyResult() {
        Graph result = extractor.getSystemRelatedGraph(fullGraph, "NonExistentSystem");

        assertTrue(result.nodes().isEmpty(), "Should return empty graph");
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void testGetMessageRelatedGraph_emptyResult() {
        Graph result = extractor.getMessageRelatedGraph(fullGraph, "UnknownType", "vX");

        assertTrue(result.nodes().isEmpty(), "Should return empty graph");
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void testGetMessageRelatedGraph_withNullVariant() {
        // Message with null variant
        Message messageWithNullVariant = Message.builder()
                .id(3L)
                .messageType("TypeC")
                .variant(null)
                .semantic(SemanticType.EVENT)
                .build();

        Reaction reaction = Reaction.builder()
                .id(300L)
                .component("ComponentZ")
                .system("SystemC")
                .build();

        Trigger trigger = Trigger.builder()
                .source(messageWithNullVariant)
                .target(reaction)
                .median(2)
                .build();

        Graph graph = new Graph(
                List.of(messageWithNullVariant, reaction),
                List.of(trigger)
        );

        Graph result = extractor.getMessageRelatedGraph(graph, "TypeC", null);

        assertEquals(2, result.nodes().size(), "Should contain message and reaction");
        assertTrue(result.nodes().contains(messageWithNullVariant));
        assertTrue(result.nodes().contains(reaction));

        assertEquals(1, result.edges().size());
        assertTrue(result.edges().contains(trigger));
    }

    @Test
    void testGetSystemRelatedGraph_withNullSystemReaction() {
        Reaction reactionWithNullSystem = Reaction.builder()
                .id(300L)
                .component("ComponentZ")
                .system(null) // bewusst null
                .build();

        Trigger triggerToNullSystemReaction = Trigger.builder()
                .source(message1)
                .target(reactionWithNullSystem)
                .median(1)
                .build();

        Graph graphWithNullSystem = new Graph(
                List.of(message1, reactionWithNullSystem),
                List.of(triggerToNullSystemReaction)
        );

        // Should return empty graph since no reaction matches the system name
        Graph result = extractor.getSystemRelatedGraph(graphWithNullSystem, "SystemA");

        assertTrue(result.nodes().isEmpty(), "Should return empty graph when system is null");
        assertTrue(result.edges().isEmpty(), "Should return empty edges when system is null");
    }

    // --- The batched forms, which have to answer exactly what the per-name forms answer -------------------

    /**
     * The batched extraction exists to make fingerprinting every subgraph of a landscape affordable, and it is
     * only worth having if it answers the same graphs. These four tests are that equivalence, over every graph
     * this class builds.
     */
    @Test
    void getSystemRelatedGraphs_answersWhatGetSystemRelatedGraphAnswers() {
        Graph graph = fullGraph;

        Map<String, Graph> batched = extractor.getSystemRelatedGraphs(graph);

        assertThat(batched.keySet()).containsExactlyInAnyOrder("systema", "systemb");
        batched.forEach((system, subgraph) -> assertSameGraph(
                extractor.getSystemRelatedGraph(graph, system), subgraph, "system " + system));
    }

    @Test
    void getComponentRelatedGraphs_answersWhatGetComponentRelatedGraphAnswers() {
        Graph graph = fullGraph;

        Map<String, Graph> batched = extractor.getComponentRelatedGraphs(graph);

        assertThat(batched.keySet()).containsExactlyInAnyOrder("ComponentX", "ComponentY");
        batched.forEach((component, subgraph) -> assertSameGraph(
                extractor.getComponentRelatedGraph(graph, component), subgraph, "component " + component));
    }

    @Test
    void getMessageRelatedGraphs_answersWhatGetMessageRelatedGraphAnswers() {
        Graph graph = fullGraph;

        Map<GraphExtractor.MessageKey, Graph> batched = extractor.getMessageRelatedGraphs(graph);

        assertThat(batched).isNotEmpty();
        batched.forEach((key, subgraph) -> assertSameGraph(
                extractor.getMessageRelatedGraph(graph, key.messageType(), key.variant()), subgraph,
                "message " + key));
    }

    @Test
    void getSystemRelatedGraphs_keysAreLowerCased_becauseTheSingleFormIgnoresCase() {
        Reaction upperCased = Reaction.builder().id(900L).component("Component9").system("SYSTEMA").build();
        Graph graph = new Graph(List.of(message1, upperCased),
                List.of(Trigger.builder().source(message1).target(upperCased).build()));

        Map<String, Graph> batched = extractor.getSystemRelatedGraphs(graph);

        assertThat(batched.keySet()).containsExactly("systema");
        assertSameGraph(extractor.getSystemRelatedGraph(graph, "SystemA"), batched.get("systema"),
                "a system spelled in upper case");
    }

    @Test
    void getSystemRelatedGraphs_aReactionWithoutASystem_isInNoSubgraph() {
        Reaction withoutSystem = Reaction.builder().id(901L).component("Component9").build();
        Graph graph = new Graph(List.of(message1, withoutSystem),
                List.of(Trigger.builder().source(message1).target(withoutSystem).build()));

        assertThat(extractor.getSystemRelatedGraphs(graph)).isEmpty();
        assertThat(extractor.getComponentRelatedGraphs(graph)).containsOnlyKeys("Component9");
    }

    @Test
    void getRelatedGraphs_ofAnEmptyGraph_areEmpty() {
        Graph empty = new Graph(List.of(), List.of());

        assertThat(extractor.getSystemRelatedGraphs(empty)).isEmpty();
        assertThat(extractor.getComponentRelatedGraphs(empty)).isEmpty();
        assertThat(extractor.getMessageRelatedGraphs(empty)).isEmpty();
    }

    /** Nodes and edges as sets: both forms build them from sets, so the order is not part of the answer. */
    private static void assertSameGraph(Graph expected, Graph actual, String what) {
        assertThat(Set.copyOf(actual.nodes()))
                .describedAs("nodes of " + what)
                .isEqualTo(Set.copyOf(expected.nodes()));
        assertThat(Set.copyOf(actual.edges()))
                .describedAs("edges of " + what)
                .isEqualTo(Set.copyOf(expected.edges()));
    }
}
