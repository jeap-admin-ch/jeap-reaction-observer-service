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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static org.junit.Assert.assertEquals;
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
     * command1/Variant1
     * ↓
     * service1 (1)
     * ├─→ event1
     * │     ├─→ service2 (1) ─→ command3
     * │     └─→ service3 (2) ─→ command4
     * └─→ command2
     */
    void givenInitialReactionsGraph() {
        // given: initial observations
        TestObservation command1 = TestObservation.ofEvent("Command1/Variant1");
        TestObservation command2 = TestObservation.ofEvent("Command2");
        TestObservation event1 = TestObservation.ofEvent("Event1");

        // and: identified reaction1 (Trigger: command1, Actions: command2 and event1)
        TestReaction testReaction1 = new TestReaction(command1, List.of(command2, event1), "reaction1");
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
        assertEquals(expectedGraph.fingerprint(), actualGraph.fingerprint());

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
        assertEquals(expectedGraph.fingerprint(), actualGraph.fingerprint());

        // and: graph structure matches
        assertGraphStructureEquals(expectedGraph, actualGraph);
    }

    void assertGraphStructureEquals(GraphWithFingerprintDto expected, GraphWithFingerprintDto actual) {
        JsonNode expectedGraph = objectMapper.valueToTree(expected.graph());
        JsonNode actualGraph = objectMapper.valueToTree(actual.graph());

        Set<JsonNode> expectedNodes = new HashSet<>();
        expectedGraph.get("nodes").forEach(expectedNodes::add);

        Set<JsonNode> actualNodes = new HashSet<>();
        actualGraph.get("nodes").forEach(actualNodes::add);

        assertEquals(expectedNodes, actualNodes);

        Set<JsonNode> expectedEdges = new HashSet<>();
        expectedGraph.get("edges").forEach(expectedEdges::add);

        Set<JsonNode> actualEdges = new HashSet<>();
        actualGraph.get("edges").forEach(actualEdges::add);

        assertEquals(expectedEdges, actualEdges);
    }
}