package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphExtractorTest {

    @Test
    void testGetSystemRelatedGraph() {
        // Create messages
        Message message1 = Message.builder()
                .id(1L)
                .messageType("TypeA")
                .variant("v1")
                .semantic(SemanticType.EVENT)
                .build();

        Message message2 = Message.builder()
                .id(2L)
                .messageType("TypeB")
                .variant("v1")
                .semantic(SemanticType.COMMAND)
                .build();

        // Create reactions
        Reaction reaction1 = Reaction.builder()
                .id(100L)
                .component("ComponentX")
                .system("SystemA")
                .build();

        Reaction reaction2 = Reaction.builder()
                .id(200L)
                .component("ComponentY")
                .system("SystemB")
                .build();

        // Create edges
        Trigger trigger1 = Trigger.builder()
                .source(message1)
                .target(reaction1)
                .median(5)
                .build();

        Action action1 = Action.builder()
                .source(reaction1)
                .target(message2)
                .build();

        Trigger unrelatedTrigger = Trigger.builder()
                .source(message2)
                .target(reaction2)
                .median(3)
                .build();

        // Build full graph
        Graph fullGraph = new Graph(
                List.of(message1, message2, reaction1, reaction2),
                List.of(trigger1, action1, unrelatedTrigger)
        );

        // Extract system-related graph
        GraphExtractor extractor = new GraphExtractor();
        Graph result = extractor.getSystemRelatedGraph(fullGraph, "SystemA");

        // Assertions
        assertEquals(3, result.nodes().size(), "Should contain 1 reaction and 2 messages");
        assertTrue(result.nodes().contains(reaction1));
        assertTrue(result.nodes().contains(message1));
        assertTrue(result.nodes().contains(message2));

        assertEquals(2, result.edges().size(), "Should contain 2 edges (trigger1 and action1)");
        assertTrue(result.edges().contains(trigger1));
        assertTrue(result.edges().contains(action1));

        // Ensure unrelated reaction and edge are not included
        assertFalse(result.nodes().contains(reaction2));
        assertFalse(result.edges().contains(unrelatedTrigger));
    }
}
