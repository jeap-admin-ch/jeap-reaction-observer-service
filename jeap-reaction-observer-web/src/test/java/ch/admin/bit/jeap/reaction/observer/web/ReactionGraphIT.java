package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.aggregation.AggregationService;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphWithFingerprintDto;
import ch.admin.bit.jeap.reaction.observer.web.service.ScheduledTasksService;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import ch.admin.bit.jeap.security.test.resource.JeapAuthenticationTestTokenBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ReactionGraphIT extends IntegrationTestBase {

    @Autowired
    AggregationService aggregationService;

    @Autowired
    ScheduledTasksService scheduledTasksService;

    @Autowired
    GraphHolder graphHolder;

    @Autowired
    ObjectMapper objectMapper;

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
        GraphWithFingerprintDto actualGraph = objectMapper.readValue(responseBody, GraphWithFingerprintDto.class);

        // and: expected graph is loaded from resource
        String expectedJson = new String(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("all_reactions_graph.json")).readAllBytes(),
                StandardCharsets.UTF_8
        );
        GraphWithFingerprintDto expectedGraph = objectMapper.readValue(expectedJson, GraphWithFingerprintDto.class);

        // then: fingerprint matches
        assertEquals(expectedGraph.fingerprint(), actualGraph.fingerprint(), "Fingerprint mismatch");

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
        GraphWithFingerprintDto actualGraph = objectMapper.readValue(responseBody, GraphWithFingerprintDto.class);

        // and: expected graph is loaded from resource
        String expectedJson = new String(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("system_related_graph.json")).readAllBytes(),
                StandardCharsets.UTF_8
        );
        GraphWithFingerprintDto expectedGraph = objectMapper.readValue(expectedJson, GraphWithFingerprintDto.class);

        // then: fingerprint matches
        assertEquals(expectedGraph.fingerprint(), actualGraph.fingerprint(), "Fingerprint mismatch");

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
        GraphWithFingerprintDto actualGraph = objectMapper.readValue(responseBody, GraphWithFingerprintDto.class);

        // and: expected graph is loaded from resource
        String expectedJson = new String(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("component_related_graph.json")).readAllBytes(),
                StandardCharsets.UTF_8
        );
        GraphWithFingerprintDto expectedGraph = objectMapper.readValue(expectedJson, GraphWithFingerprintDto.class);

        // then: fingerprint matches
        assertEquals(expectedGraph.fingerprint(), actualGraph.fingerprint(), "Fingerprint mismatch");

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
        Map<String, GraphWithFingerprintDto> actualGraphs = objectMapper.readValue(responseBody,
                objectMapper.getTypeFactory().constructMapType(
                        java.util.Map.class,
                        String.class,
                        GraphWithFingerprintDto.class
                ));

        // and: expected graph is loaded from resource
        String expectedJson = new String(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("message_related_graph.json")).readAllBytes(),
                StandardCharsets.UTF_8
        );

        Map<String, GraphWithFingerprintDto> expectedGraphs = objectMapper.readValue(expectedJson,
                objectMapper.getTypeFactory().constructMapType(
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

            assertEquals(expected.fingerprint(), actual.fingerprint(), "Fingerprint mismatch for variant: " + key);
            assertGraphStructureEquals(expected, actual);
        }
    }

    void assertGraphStructureEquals(GraphWithFingerprintDto expected, GraphWithFingerprintDto actual) {
        JsonNode expectedGraph = objectMapper.valueToTree(expected.graph());
        JsonNode actualGraph = objectMapper.valueToTree(actual.graph());

        Set<JsonNode> expectedNodes = new HashSet<>();
        expectedGraph.get("nodes").forEach(expectedNodes::add);

        Set<JsonNode> actualNodes = new HashSet<>();
        actualGraph.get("nodes").forEach(actualNodes::add);

        assertEquals(expectedNodes, actualNodes, "Mismatch in graph nodes");

        Set<JsonNode> expectedEdges = new HashSet<>();
        expectedGraph.get("edges").forEach(expectedEdges::add);

        Set<JsonNode> actualEdges = new HashSet<>();
        actualGraph.get("edges").forEach(actualEdges::add);

        assertEquals(expectedEdges, actualEdges, "Mismatch in graph edges");
    }
}