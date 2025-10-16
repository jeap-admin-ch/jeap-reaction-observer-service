package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
}
