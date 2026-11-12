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

    // The reactions of an invented order service and partner service, made up to have interfaces of every kind:
    //   [submitted] --> [checkOrder] --> [checked], [reserveCredit], [nightlyJob]
    //   [reserveCredit] --> [reserve] --> [reserved]
    //   [startOrder] --> [start] --> [submitted]
    private final Message submitted = new Message(11, "OrderSubmitted", null, SemanticType.EVENT);
    private final Message checked = new Message(12, "OrderChecked", null, SemanticType.EVENT);
    private final Message reserved = new Message(13, "CreditReserved", null, SemanticType.EVENT);
    private final HttpOperation reserveCredit = new HttpOperation(14, "partner-service", "POST", "/api/credits");
    private final HttpOperation startOrder = new HttpOperation(15, "order-service", "POST", "/api/orders");
    private final GenericInterface nightlyJob = new GenericInterface(16, "scheduled", "nightly-job");
    private final Reaction checkOrder = Reaction.builder().id(11L).component("order-service").system("shop").build();
    private final Reaction reserve = Reaction.builder().id(12L).component("partner-service").system("shop").build();
    private final Reaction start = Reaction.builder().id(13L).component("order-service").system("shop").build();
    private final Trigger submittedTriggersCheck = Trigger.builder().source(submitted).target(checkOrder).build();
    private final Action checkSendsChecked = Action.builder().source(checkOrder).target(checked).build();
    private final Action checkCallsReserve = Action.builder().source(checkOrder).target(reserveCredit).build();
    private final Action checkStartsJob = Action.builder().source(checkOrder).target(nightlyJob).build();
    private final Trigger callTriggersReserve = Trigger.builder().source(reserveCredit).target(reserve).build();
    private final Action reserveSendsReserved = Action.builder().source(reserve).target(reserved).build();
    private final Trigger startTriggersStart = Trigger.builder().source(startOrder).target(start).build();
    private final Action startSendsSubmitted = Action.builder().source(start).target(submitted).build();
    private final Graph mixedGraph = new Graph(
            List.of(submitted, checked, reserved, reserveCredit, startOrder, nightlyJob, checkOrder, reserve, start),
            List.of(submittedTriggersCheck, checkSendsChecked, checkCallsReserve, checkStartsJob,
                    callTriggersReserve, reserveSendsReserved, startTriggersStart, startSendsSubmitted));

    @Test
    void getInterfaceRelatedGraph_ofAnHttpOperation_hasEveryReactionOfItWhole_andNoneOfItsNeighbours() {
        Graph graph = extractor.getInterfaceRelatedGraph(mixedGraph, reserveCredit.id());

        // The reaction calling the operation with its trigger and all its actions, of every kind of interface,
        // and the reaction the operation triggers - but not the reaction that only sends the neighbour [submitted]
        assertThat(graph.nodes()).containsExactlyInAnyOrder(
                reserveCredit, checkOrder, submitted, checked, nightlyJob, reserve, reserved);
        assertThat(graph.edges()).containsExactlyInAnyOrder(submittedTriggersCheck, checkSendsChecked,
                checkCallsReserve, checkStartsJob, callTriggersReserve, reserveSendsReserved);
    }

    @Test
    void getInterfaceRelatedGraph_ofAMessage_hasItsReactionsWholeWithTheirOtherKindsOfInterfaces() {
        Graph graph = extractor.getInterfaceRelatedGraph(mixedGraph, submitted.id());

        // The reaction sending the message is triggered by an HTTP operation, the one it triggers calls one
        assertThat(graph.nodes()).containsExactlyInAnyOrder(
                submitted, start, startOrder, checkOrder, checked, reserveCredit, nightlyJob);
        assertThat(graph.edges()).containsExactlyInAnyOrder(startTriggersStart, startSendsSubmitted,
                submittedTriggersCheck, checkSendsChecked, checkCallsReserve, checkStartsJob);
    }

    @Test
    void getInterfaceRelatedGraph_ofAnIdThatIsNoInterface_isEmpty() {
        // 11 is the id of an interface and of a reaction, 99 of none, and 13 the id of a reaction as well
        assertThat(extractor.getInterfaceRelatedGraph(mixedGraph, 99).nodes()).isEmpty();
        assertThat(extractor.getInterfaceRelatedGraph(mixedGraph, 11).nodes()).contains(submitted);
        assertThat(extractor.getInterfaceRelatedGraph(mixedGraph, 13).nodes()).contains(reserved).doesNotContain(start);
    }

    @Test
    void getInterfaceRelatedGraphs_answersWhatGetInterfaceRelatedGraphAnswers() {
        Map<Long, Graph> batched = extractor.getInterfaceRelatedGraphs(mixedGraph);

        assertThat(batched).containsOnlyKeys(11L, 12L, 13L, 14L, 15L, 16L);
        batched.forEach((id, subgraph) -> {
            assertSameGraph(extractor.getInterfaceRelatedGraph(mixedGraph, id), subgraph, "interface " + id);
            assertEdgesAreWiredToNodes(subgraph);
        });
    }

    @Test
    void getInterfaceRelatedGraphs_ofAGraphOfMessages_areTheMessageRelatedGraphs() {
        Map<Long, Graph> byInterface = extractor.getInterfaceRelatedGraphs(fullGraph);

        assertSameGraph(extractor.getMessageRelatedGraph(fullGraph, "TypeA", "v1"), byInterface.get(1L), "TypeA");
        assertSameGraph(extractor.getMessageRelatedGraph(fullGraph, "TypeB", "v1"), byInterface.get(2L), "TypeB");
    }

    /** Nodes and edges as sets: both forms build them from sets, so the order is not part of the answer. */
    @Test
    void everySubgraph_keepsItsEdgesWiredToItsNodes() {
        // The extractor matches an edge to its node by value, so a node rebuilt with one field changed - a
        // median applied to a finished graph, say - would leave every subgraph with its nodes and no edges.
        Reaction counted = Reaction.builder()
                .id(300L).component("ComponentZ").system("SystemC").median(9252).build();
        Message trigger = Message.builder()
                .id(3L).messageType("TypeC").variant("v1").semantic(SemanticType.EVENT).build();
        Graph graph = new Graph(
                List.of(message1, message2, reaction1, reaction2, counted, trigger),
                List.of(trigger1, action1, unrelatedTrigger,
                        Trigger.builder().source(trigger).target(counted).median(9252).build()));

        assertEdgesAreWiredToNodes(graph);
        extractor.getSystemRelatedGraphs(graph).values().forEach(GraphExtractorTest::assertEdgesAreWiredToNodes);
        extractor.getComponentRelatedGraphs(graph).values().forEach(GraphExtractorTest::assertEdgesAreWiredToNodes);
        extractor.getMessageRelatedGraphs(graph).values().forEach(GraphExtractorTest::assertEdgesAreWiredToNodes);

        // And the subgraph of the counted reaction really has its edge, rather than passing the assertion by
        // having none at all
        assertThat(extractor.getSystemRelatedGraph(graph, "SystemC").edges()).hasSize(1);
    }

    private static void assertEdgesAreWiredToNodes(Graph graph) {
        Set<Node> nodes = Set.copyOf(graph.nodes());
        assertThat(graph.edges()).allSatisfy(edge -> {
            if (edge instanceof Trigger triggerEdge) {
                assertThat(nodes).contains(triggerEdge.source(), triggerEdge.target());
            } else if (edge instanceof Action actionEdge) {
                assertThat(nodes).contains(actionEdge.source(), actionEdge.target());
            }
        });
    }

    private static void assertSameGraph(Graph expected, Graph actual, String what) {
        assertThat(Set.copyOf(actual.nodes()))
                .describedAs("nodes of " + what)
                .isEqualTo(Set.copyOf(expected.nodes()));
        assertThat(Set.copyOf(actual.edges()))
                .describedAs("edges of " + what)
                .isEqualTo(Set.copyOf(expected.edges()));
    }
}
