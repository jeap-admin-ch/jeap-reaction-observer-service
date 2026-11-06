package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.aggregation.AggregationService;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.GenericInterface;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.HttpOperation;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionsObservedEventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;
import ch.admin.bit.jeap.reaction.observer.web.service.ScheduledTasksService;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import ch.admin.bit.jeap.security.test.resource.JeapAuthenticationTestTokenBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reactions with HTTP requests and with observation types the service does not know, and counts of reactions it
 * does not know: all of them are accepted, the graph is built, and the API v1 shows the messages only.
 */
class ReactionGraphExtensionsIT extends IntegrationTestBase {

    private static final String READ_ROLE = "reactionobserver_@reactions_#read";
    private static final String SYSTEM = "shop";

    private static final TestObservation POST_ORDER = TestObservation.ofHttpRequest("order-service", "POST", "/api/orders");
    private static final TestObservation PUT_STOCK = TestObservation.ofHttpRequest("stock-service", "PUT", "/api/stock/{id}");
    private static final TestObservation ORDER_CREATED = TestObservation.ofEvent("OrderCreated");
    private static final TestObservation SEND_REPORT = TestObservation.ofCommand("SendReport");
    private static final TestObservation REPORT_SENT = TestObservation.ofEvent("ReportSent");
    private static final TestObservation NIGHTLY_REPORT = TestObservation.ofType("scheduled", "nightly-report");
    private static final TestObservation UNPARSABLE_HTTP = TestObservation.ofType("http_request", "not-an-operation");
    /** 1000 characters, beyond the former 255, with characters that are encoded in the id */
    private static final TestObservation LONG_HTTP = TestObservation.ofHttpRequest("archive-service", "PUT",
            "/api/archive/" + "a:#&%".repeat((1000 - "archive-service PUT /api/archive/".length()) / 5) + "x".repeat(
                    (1000 - "archive-service PUT /api/archive/".length()) % 5));

    @Autowired
    AggregationService aggregationService;
    @Autowired
    ScheduledTasksService scheduledTasksService;
    @Autowired
    GraphHolder graphHolder;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    MeterRegistry meterRegistry;

    @Test
    void reactionsWithHttpRequestsAndUnknownTypes_areStored_theGraphIsBuilt_andTheV1ShowsTheMessagesOnly()
            throws Exception {
        assertThat(LONG_HTTP.fqn()).hasSize(1000);
        double unparsableBefore = counter("reaction_observer_service_unparsable_interfaces", "http_request");

        // given: a REST-triggered reaction with a message action and a REST action
        TestReaction restTriggered = new TestReaction(POST_ORDER, List.of(ORDER_CREATED, PUT_STOCK), "restTriggered");
        // and: a reaction triggered by REST only, without any message
        TestReaction restOnly = new TestReaction(PUT_STOCK, null, "restOnly");
        // and: a reaction triggered by a type the service does not know
        TestReaction scheduled = new TestReaction(NIGHTLY_REPORT, List.of(SEND_REPORT), "scheduled");
        // and: a reaction triggered by an HTTP request whose FQN cannot be parsed
        TestReaction unparsable = new TestReaction(UNPARSABLE_HTTP, List.of(REPORT_SENT), "unparsable");
        // and: a reaction of a message, with an action to an HTTP request with a long FQN
        TestReaction longFqn = new TestReaction(ORDER_CREATED, List.of(LONG_HTTP), "longFqn");

        observe(restTriggered, "order-service");
        observe(restOnly, "stock-service");
        observe(scheduled, "report-service");
        observe(unparsable, "report-service");
        observe(longFqn, "archive-service");

        // when: the graph is built
        aggregationService.aggregateData(getToday());
        scheduledTasksService.refreshReactionGraphInternal();

        // then: the internal graph has a node of each kind
        Graph fullGraph = graphHolder.getFullGraph();
        assertThat(fullGraph.nodes()).filteredOn(HttpOperation.class::isInstance)
                .extracting(node -> ((HttpOperation) node).provider() + " " + ((HttpOperation) node).method())
                .containsExactlyInAnyOrder("order-service POST", "stock-service PUT", "archive-service PUT");
        assertThat(fullGraph.nodes()).filteredOn(GenericInterface.class::isInstance)
                .extracting(node -> ((GenericInterface) node).type() + " " + ((GenericInterface) node).fqn())
                .containsExactlyInAnyOrder("scheduled nightly-report", "http_request not-an-operation");
        assertThat(fullGraph.nodes()).filteredOn(Message.class::isInstance)
                .extracting(node -> ((Message) node).messageType())
                .containsExactlyInAnyOrder("OrderCreated", "SendReport", "ReportSent");
        // and: the FQN that cannot be parsed is counted
        assertThat(counter("reaction_observer_service_unparsable_interfaces", "http_request"))
                .isEqualTo(unparsableBefore + 1);

        // and: the v1 has message and reaction nodes only, and the message part of each reaction
        JsonNode graph = getJson("/api/graphs").get("graph");
        assertThat(nodeTypes(graph)).containsOnly("MESSAGE", "REACTION");
        assertThat(edgesByMeaning(graph)).containsExactlyInAnyOrder(
                "ACTION order-service -> OrderCreated",
                "ACTION report-service -> SendReport",
                "ACTION report-service -> ReportSent",
                "TRIGGER OrderCreated -> archive-service");
        graph.get("edges").forEach(edge -> assertThat(
                edge.has("sourceNodeType") ? edge.get("sourceNodeType").asString() : edge.get("targetNodeType").asString())
                .isEqualTo("MESSAGE"));

        // and: the component with a REST-only reaction has no graph in the v1, and is not in its index
        mvc.perform(get("/api/graphs/components/stock-service").with(authentication(readRole())))
                .andExpect(status().isNotFound());
        assertThat(getJson("/api/graphs/components").get("entries").valueStream()
                .map(entry -> entry.get("name").asString()))
                .containsExactlyInAnyOrder("order-service", "report-service", "archive-service");
    }

    @Test
    void countsOfAnUnknownReaction_areSkippedAndCounted_andTheKnownCountsOfTheEventAreSaved() throws Exception {
        // given: an identified reaction
        TestReaction known = new TestReaction(ORDER_CREATED, List.of(SEND_REPORT), "known");
        sendAndAwaitReactionPersistence(known, SYSTEM, "report-service");
        double skippedBefore = meterRegistry.get("reaction_observer_service_unknown_reaction_counts").counter().count();

        // when: an instance reports the count of the known reaction together with one of an unknown reaction
        Instant now = Instant.now();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(known.id(), 4);
        counts.put("notYetIdentified", 2);
        ReactionsObservedEvent event = new ReactionsObservedEventBuilder("report-service", SYSTEM)
                .serviceInstanceIdentifier(UUID.randomUUID())
                .countByReactionId(counts)
                .timeframe(now.minusSeconds(300), now)
                .build();
        sendSync("reactions-observed", event);

        // then: the known count is saved, and the unknown one is skipped and counted
        await().until(() -> observedReactionIsPersisted(event.getIdentity().getIdempotenceId()) == 1);
        assertThat(meterRegistry.get("reaction_observer_service_unknown_reaction_counts").counter().count())
                .isEqualTo(skippedBefore + 1);

        // and: the known count is in the statistics
        aggregationService.aggregateData(getToday());
        assertThat(getJson("/api/statistics/last-observation-date").get("report-service").asString())
                .isEqualTo(getToday().toString());
    }

    private void observe(TestReaction reaction, String component) {
        sendAndAwaitReactionPersistence(reaction, SYSTEM, component);
        sendAndAwaitObservedEventForReaction(reaction, SYSTEM, component, 3);
    }

    private JsonNode getJson(String path) throws Exception {
        String body = mvc.perform(get(path).with(authentication(readRole())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body);
    }

    private static List<String> nodeTypes(JsonNode graph) {
        List<String> types = new ArrayList<>();
        graph.get("nodes").forEach(node -> types.add(node.get("nodeType").asString()));
        return types;
    }

    /** Every edge by the message type and the component it connects - ids are unique per node type only. */
    private static List<String> edgesByMeaning(JsonNode graph) {
        Map<Long, String> messages = new HashMap<>();
        Map<Long, String> reactions = new HashMap<>();
        graph.get("nodes").forEach(node -> {
            if ("MESSAGE".equals(node.get("nodeType").asString())) {
                messages.put(node.get("id").asLong(), node.get("messageType").asString());
            } else {
                reactions.put(node.get("id").asLong(), node.get("component").asString());
            }
        });
        List<String> edges = new ArrayList<>();
        graph.get("edges").forEach(edge -> {
            if ("TRIGGER".equals(edge.get("edgeType").asString())) {
                edges.add("TRIGGER " + messages.get(edge.get("sourceId").asLong()) + " -> "
                          + reactions.get(edge.get("targetReactionId").asLong()));
            } else {
                edges.add("ACTION " + reactions.get(edge.get("sourceReactionId").asLong()) + " -> "
                          + messages.get(edge.get("targetId").asLong()));
            }
        });
        return edges;
    }

    private double counter(String name, String type) {
        Counter counter = meterRegistry.find(name).tag("type", type).counter();
        return counter == null ? 0 : counter.count();
    }

    private static JeapAuthenticationToken readRole() {
        return JeapAuthenticationTestTokenBuilder.create().withUserRoles(READ_ROLE).build();
    }
}
