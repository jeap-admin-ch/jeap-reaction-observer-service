package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.aggregation.AggregationService;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphWithFingerprintDto;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import ch.admin.bit.jeap.reaction.observer.web.service.ScheduledTasksService;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import ch.admin.bit.jeap.security.test.resource.JeapAuthenticationTestTokenBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reaction graphs over the API, against the expected graphs in {@code src/test/resources}.
 * <p>
 * <b>The comparison ignores the database ids of the nodes</b>, and has to. The id sequences have
 * {@code allocationSize = 10}, so Hibernate hands ids out of a block it caches in the session factory - a
 * block that survives the {@code flyway.clean()} between two tests. The first test to insert gets 1..4, the
 * next 5..8, and the fixtures can only carry one of those.
 * <p>
 * That the fixtures' ids used to match in every test was an accident of a bug: the graph refresh was
 * {@code @SchedulerLock}ed with {@code lockAtLeastFor = 5s}, so the second and every later call in this class
 * refreshed nothing and each test asserted against the graph the <em>first</em> test had built. The lock is
 * gone - the refresh writes nothing, and an instance that does not rebuild its own copy serves a stale one
 * for as long as it lives - so each test now really refreshes and really sees its own ids.
 * <p>
 * So a node is compared by what it <em>is</em> - a message type and variant, or a component - and an edge by
 * the nodes it connects and the median it carries. The fingerprint is checked for what it promises: that it
 * covers the graph that was answered. That the algorithm itself does not drift is
 * {@code GraphFingerprintCalculatorTest}'s golden value over a fixed graph; that an index entry carries the
 * same value as its resource is {@code GraphSnapshotFactoryTest}'s.
 */
class ReactionGraphIT extends IntegrationTestBase {

    @Autowired
    AggregationService aggregationService;

    @Autowired
    ScheduledTasksService scheduledTasksService;

    @Autowired
    GraphHolder graphHolder;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    GraphFingerprintCalculator fingerprintCalculator;

    /**
     * Command1/Variant1
     * ↓
     * service1 (1)
     * ├─→ Event1
     * │     ├─→ service2 (1) ─→ Command3
     * │     └─→ service3 (2) ─→ Command4
     * └──→ Command1/Variant2 ─→ service4 (2) ─→ Command2
     */
    void givenInitialReactionsGraph() {
        // given: initial observations
        TestObservation command1v1 = TestObservation.ofEvent("Command1/Variant1");
        TestObservation command1v2 = TestObservation.ofEvent("Command1/Variant2");
        TestObservation event1 = TestObservation.ofEvent("Event1");

        // and: identified reaction1 (Trigger: command1v1, Actions: command1v2 and event1)
        TestReaction testReaction1 = new TestReaction(command1v1, List.of(command1v2, event1), "reaction1");
        sendAndAwaitReactionPersistence(testReaction1, "system1", "service1");
        sendAndAwaitObservedEventForReaction(testReaction1, "system1", "service1", 5);

        // and: identified reaction2 (Trigger: event1, Actions: command3)
        TestObservation command3 = TestObservation.ofEvent("Command3");
        TestReaction testReaction2 = new TestReaction(event1, List.of(command3), "reaction2");
        sendAndAwaitReactionPersistence(testReaction2, "system1", "service2");
        sendAndAwaitObservedEventForReaction(testReaction2, "system1", "service2", 7);

        // and: identified reaction3 (Trigger: event1, Actions: command4)
        TestObservation command4 = TestObservation.ofEvent("Command4");
        TestReaction testReaction3 = new TestReaction(event1, List.of(command4), "reaction3");
        sendAndAwaitReactionPersistence(testReaction3, "system2", "service3");
        sendAndAwaitObservedEventForReaction(testReaction3, "system2", "service3", 200);

        // and: identified reaction4 (Trigger: command1v2, Actions: command2)
        TestObservation command2 = TestObservation.ofEvent("Command2");
        TestReaction testReaction4 = new TestReaction(command1v2, List.of(command2), "reaction4");
        sendAndAwaitReactionPersistence(testReaction4, "system2", "service4");
        sendAndAwaitObservedEventForReaction(testReaction4, "system2", "service4", 0);

        // when: aggregate data
        aggregationService.aggregateData(getToday());

        // and: manually trigger the scheduled graph refresh
        scheduledTasksService.scheduledRefreshReactionGraph();

        // then: graph is built and stored
        Graph graph = graphHolder.getGraph();
        assertNotNull(graph);
        assertFalse(graph.nodes().isEmpty());
        assertFalse(graph.edges().isEmpty());
    }

    @Test
    void test_all_reactions_graph() throws Exception {
        givenInitialReactionsGraph();

        // when: all reactions graph is called
        JeapAuthenticationToken authentication = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("reaction-observer-read")
                .build();

        var result = mvc.perform(get("/api/graphs")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        GraphWithFingerprintDto actualGraph = jsonMapper.readValue(responseBody, GraphWithFingerprintDto.class);

        // and: expected graph is loaded from resource
        String expectedJson = new String(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("all_reactions_graph.json")).readAllBytes(),
                StandardCharsets.UTF_8
        );
        GraphWithFingerprintDto expectedGraph = jsonMapper.readValue(expectedJson, GraphWithFingerprintDto.class);

        // then: the fingerprint covers the graph that was answered
        assertFingerprintCoversTheGraph(actualGraph);

        // and: graph structure matches
        assertGraphStructureEquals(expectedGraph, actualGraph);
    }

    @Test
    void test_system_related_graph() throws Exception {
        givenInitialReactionsGraph();

        // when: all system related graph is called
        JeapAuthenticationToken authentication = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("reaction-observer-read")
                .build();

        var result = mvc.perform(get("/api/graphs/systems/system1")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        GraphWithFingerprintDto actualGraph = jsonMapper.readValue(responseBody, GraphWithFingerprintDto.class);

        // and: expected graph is loaded from resource
        String expectedJson = new String(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("system_related_graph.json")).readAllBytes(),
                StandardCharsets.UTF_8
        );
        GraphWithFingerprintDto expectedGraph = jsonMapper.readValue(expectedJson, GraphWithFingerprintDto.class);

        // then: the fingerprint covers the graph that was answered
        assertFingerprintCoversTheGraph(actualGraph);

        // and: graph structure matches
        assertGraphStructureEquals(expectedGraph, actualGraph);
    }

    @Test
    void test_component_related_graph() throws Exception {
        givenInitialReactionsGraph();

        // when: all component related graph is called
        JeapAuthenticationToken authentication = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("reaction-observer-read")
                .build();

        var result = mvc.perform(get("/api/graphs/components/service2")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        GraphWithFingerprintDto actualGraph = jsonMapper.readValue(responseBody, GraphWithFingerprintDto.class);

        // and: expected graph is loaded from resource
        String expectedJson = new String(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("component_related_graph.json")).readAllBytes(),
                StandardCharsets.UTF_8
        );
        GraphWithFingerprintDto expectedGraph = jsonMapper.readValue(expectedJson, GraphWithFingerprintDto.class);

        // then: the fingerprint covers the graph that was answered
        assertFingerprintCoversTheGraph(actualGraph);

        // and: graph structure matches
        assertGraphStructureEquals(expectedGraph, actualGraph);
    }

    @Test
    void test_message_related_graph() throws Exception {
        givenInitialReactionsGraph();

        // when: all component related graph is called
        JeapAuthenticationToken authentication = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("reaction-observer-read")
                .build();

        var result = mvc.perform(get("/api/graphs/messages/Command1")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, GraphWithFingerprintDto> actualGraphs = jsonMapper.readValue(responseBody,
                jsonMapper.getTypeFactory().constructMapType(
                        java.util.Map.class,
                        String.class,
                        GraphWithFingerprintDto.class
                ));

        // and: expected graph is loaded from resource
        String expectedJson = new String(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("message_related_graph.json")).readAllBytes(),
                StandardCharsets.UTF_8
        );

        Map<String, GraphWithFingerprintDto> expectedGraphs = jsonMapper.readValue(expectedJson,
                jsonMapper.getTypeFactory().constructMapType(
                        java.util.Map.class,
                        String.class,
                        GraphWithFingerprintDto.class
                ));

        // then: all expected graphs for message variants are present
        assertEquals(expectedGraphs.keySet(), actualGraphs.keySet(), "Mismatch in message variants");

        // then: fingerprints are matching and graph structure matches for each graph
        for (String key : expectedGraphs.keySet()) {
            GraphWithFingerprintDto expected = expectedGraphs.get(key);
            GraphWithFingerprintDto actual = actualGraphs.get(key);

            assertFingerprintCoversTheGraph(actual);
            assertGraphStructureEquals(expected, actual);
        }
    }

    @Test
    void test_reaction_only_shown_after_being_observed() {
        // given: a reaction is identified but never observed
        TestObservation lonelyTrigger = TestObservation.ofEvent("LonelyTrigger");
        TestObservation lonelyAction = TestObservation.ofEvent("LonelyAction");
        TestReaction lonelyReaction = new TestReaction(lonelyTrigger, List.of(lonelyAction), "lonelyReaction");
        sendAndAwaitReactionPersistence(lonelyReaction, "lonelySystem", "lonelyService");

        // when: data is aggregated and the graph refreshed
        aggregationService.aggregateData(getToday());
        scheduledTasksService.refreshReactionGraphInternal();

        // then: the unobserved reaction is not presented in the graph
        assertFalse(graphContainsComponent(graphHolder.getGraph(), "lonelyService"),
                "a reaction that has not been observed must not be shown");

        // when: the reaction is observed and data is re-aggregated
        sendAndAwaitObservedEventForReaction(lonelyReaction, "lonelySystem", "lonelyService", 3);
        aggregationService.aggregateData(getToday());
        scheduledTasksService.refreshReactionGraphInternal();

        // then: the reaction is presented again
        assertTrue(graphContainsComponent(graphHolder.getGraph(), "lonelyService"),
                "a reaction that has been observed must be shown again");
    }

    private boolean graphContainsComponent(Graph graph, String component) {
        return graph.nodes().stream()
                .filter(node -> node instanceof ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction)
                .map(node -> (ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction) node)
                .anyMatch(reaction -> component.equals(reaction.component()));
    }

    void assertGraphStructureEquals(GraphWithFingerprintDto expected, GraphWithFingerprintDto actual) {
        assertEquals(nodesByMeaning(expected), nodesByMeaning(actual), "Mismatch in graph nodes");
        assertEquals(edgesByMeaning(expected), edgesByMeaning(actual), "Mismatch in graph edges");
    }

    /**
     * That the fingerprint in the body is the one of the graph in the body - which is what a consumer compares
     * an index entry and an {@code ETag} against.
     */
    void assertFingerprintCoversTheGraph(GraphWithFingerprintDto answered) {
        assertEquals(fingerprintCalculator.calculate(answered.graph()), answered.fingerprint(),
                "The fingerprint does not cover the graph it was answered with");
    }

    /**
     * Every node by what it is rather than by its database id: a message by its type and variant, a reaction
     * by its component.
     */
    private Set<String> nodesByMeaning(GraphWithFingerprintDto graph) {
        Set<String> nodes = new HashSet<>();
        jsonMapper.valueToTree(graph.graph()).get("nodes").forEach(node -> nodes.add(meaningOf(node)));
        return nodes;
    }

    /**
     * Every edge by the nodes it connects, named the same way, and the median it carries.
     * <p>
     * The two kinds of node are resolved separately: <b>an id is unique per node type, not across them</b>, so
     * one map keyed by id alone would resolve a reaction to a message of the same number.
     */
    private Set<String> edgesByMeaning(GraphWithFingerprintDto graph) {
        JsonNode tree = jsonMapper.valueToTree(graph.graph());
        Map<Long, String> messages = new HashMap<>();
        Map<Long, String> reactions = new HashMap<>();
        tree.get("nodes").forEach(node -> {
            if ("MESSAGE".equals(node.get("nodeType").asString())) {
                messages.put(idOf(node), meaningOf(node));
            } else {
                reactions.put(idOf(node), meaningOf(node));
            }
        });

        Set<String> edges = new HashSet<>();
        tree.get("edges").forEach(edge -> {
            if ("TRIGGER".equals(edge.get("edgeType").asString())) {
                edges.add("TRIGGER " + messages.get(edge.get("sourceId").asLong())
                          + " -> " + reactions.get(edge.get("targetReactionId").asLong())
                          + " median=" + edge.get("median"));
            } else {
                edges.add("ACTION " + reactions.get(edge.get("sourceReactionId").asLong())
                          + " -> " + messages.get(edge.get("targetId").asLong()));
            }
        });
        return edges;
    }

    private static String meaningOf(JsonNode node) {
        String nodeType = node.get("nodeType").asString();
        if ("MESSAGE".equals(nodeType)) {
            JsonNode variant = node.get("variant");
            return "MESSAGE " + node.get("messageType").asString()
                   + (variant == null || variant.isNull() ? "" : "/" + variant.asString());
        }
        return "REACTION " + node.get("component").asString();
    }

    /**
     * The id of a node, which is only used to resolve an edge's ends within the same answer - never compared
     * across two of them.
     */
    private static long idOf(JsonNode node) {
        return node.get("id").asLong();
    }
}
