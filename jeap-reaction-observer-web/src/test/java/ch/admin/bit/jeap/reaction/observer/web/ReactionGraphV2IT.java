package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.aggregation.AggregationService;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;
import ch.admin.bit.jeap.reaction.observer.web.service.ScheduledTasksService;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import ch.admin.bit.jeap.security.test.resource.JeapAuthenticationTestTokenBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ch.admin.bit.jeap.reaction.observer.domain.aggregation.TimeUtils.getToday;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API v2, from the events to the answers: reactions with messages, HTTP requests and a type the service does
 * not know are all in its graphs, where the API v1 keeps showing the messages only.
 */
class ReactionGraphV2IT extends IntegrationTestBase {

    private static final String READ_ROLE = "reactionobserver_@reactions_#read";
    private static final String SYSTEM = "shop";
    private static final String SCHEMAS = "#/components/schemas/";

    private static final TestObservation POST_ORDER = TestObservation.ofHttpRequest("order-service", "POST", "/api/orders");
    private static final TestObservation PUT_STOCK = TestObservation.ofHttpRequest("stock-service", "PUT", "/api/stock/{}");
    private static final TestObservation ORDER_CREATED = TestObservation.ofEvent("OrderCreated");
    private static final TestObservation STOCK_CHANGED = TestObservation.ofEvent("StockChanged");
    private static final TestObservation SEND_REPORT = TestObservation.ofCommand("SendReport");
    private static final TestObservation NIGHTLY_REPORT = TestObservation.ofType("scheduled", "nightly-report");
    private static final TestObservation UNPARSABLE_HTTP = TestObservation.ofType("http_request", "not-an-operation");
    /** An operation the caller named with the called host, because the called service did not name it. */
    private static final TestObservation POST_TO_HOST = TestObservation.ofHttpRequest("//foo.bar.admin.ch", "POST", "/{*}");

    @Autowired
    AggregationService aggregationService;
    @Autowired
    ScheduledTasksService scheduledTasksService;
    @Autowired
    JsonMapper jsonMapper;
    @Value("${server.servlet.context-path:}")
    String contextPath;

    @Test
    void theV2_showsReactionsWithEveryKindOfInterface_whereTheV1ShowsTheMessagesOnly() throws Exception {
        // given: a REST-triggered reaction with a message action and a REST action
        observe(new TestReaction(POST_ORDER, List.of(ORDER_CREATED, PUT_STOCK), "placeOrder"), "order-service");
        // and: the reaction to that REST action, with a message action
        observe(new TestReaction(PUT_STOCK, List.of(STOCK_CHANGED), "changeStock"), "stock-service");
        // and: a reaction to the message of the second one, which is not connected to PUT_STOCK
        observe(new TestReaction(STOCK_CHANGED, List.of(SEND_REPORT), "reportStock"), "report-service");
        // and: reactions triggered by a type the service does not know, and by an HTTP request it cannot parse
        observe(new TestReaction(NIGHTLY_REPORT, List.of(SEND_REPORT), "scheduled"), "report-service");
        observe(new TestReaction(UNPARSABLE_HTTP, List.of(SEND_REPORT), "unparsable"), "report-service");

        aggregationService.aggregateData(getToday());
        scheduledTasksService.refreshReactionGraphInternal();

        // then: the v2 has a node of each kind, each interface with its type and FQN
        JsonNode graph = getJson("/api/v2/graphs").get("graph");
        assertThat(interfaces(graph)).containsExactlyInAnyOrder(
                "HTTP_OPERATION http_request order-service POST /api/orders",
                "HTTP_OPERATION http_request stock-service PUT /api/stock/{}",
                "MESSAGE event OrderCreated",
                "MESSAGE event StockChanged",
                "MESSAGE command SendReport",
                "INTERFACE scheduled nightly-report",
                "INTERFACE http_request not-an-operation");
        JsonNode putStock = nodeWithFqn(graph, PUT_STOCK.fqn());
        assertThat(putStock.get("provider").asString()).isEqualTo("stock-service");
        assertThat(putStock.get("method").asString()).isEqualTo("PUT");
        assertThat(putStock.get("path").asString()).isEqualTo("/api/stock/{}");

        // and: every reaction has its component, system and median, and the neutral values of the other fields
        List<JsonNode> reactions = nodesOfType(graph, "REACTION");
        assertThat(reactions).hasSize(5).allSatisfy(reaction -> {
            assertThat(reaction.get("system").asString()).isEqualTo(SYSTEM);
            assertThat(reaction.get("median").asInt()).isEqualTo(3);
            assertThat(reaction.get("status").asString()).isEqualTo("ACTIVE");
            assertThat(reaction.get("reactionModelVersion").asInt()).isEqualTo(1);
            assertThat(reaction.has("interfaceType")).isFalse();
        });

        // and: the ids are unique, and every edge is its type and two ids of nodes of the graph
        Map<String, String> namesById = namesById(graph);
        assertThat(namesById).hasSize(graph.get("nodes").size());
        assertThat(edgesByMeaning(graph, namesById)).containsExactlyInAnyOrder(
                "TRIGGER order-service POST /api/orders -> order-service",
                "ACTION order-service -> OrderCreated",
                "ACTION order-service -> stock-service PUT /api/stock/{}",
                "TRIGGER stock-service PUT /api/stock/{} -> stock-service",
                "ACTION stock-service -> StockChanged",
                "TRIGGER StockChanged -> report-service",
                "TRIGGER nightly-report -> report-service",
                "TRIGGER not-an-operation -> report-service",
                // one action of each of the three reactions of the report service
                "ACTION report-service -> SendReport",
                "ACTION report-service -> SendReport",
                "ACTION report-service -> SendReport");

        // and: the v1 shows the same data with messages only
        JsonNode graphV1 = getJson("/api/graphs").get("graph");
        assertThat(nodesOfType(graphV1, "MESSAGE")).extracting(node -> node.get("messageType").asString())
                .containsExactlyInAnyOrder("OrderCreated", "StockChanged", "SendReport");
        assertThat(nodesOfType(graphV1, "REACTION")).hasSize(5);
        assertThat(graphV1.get("nodes")).hasSize(8);

        // and: the views of the v2 have the reactions without a message, with entity tags of their own
        assertThat(names(getJson("/api/v2/graphs/components")))
                .containsExactly("order-service", "report-service", "stock-service");
        String tagV1 = fetch("/api/graphs/systems/" + SYSTEM).getResponse().getHeader(HttpHeaders.ETAG);
        String tagV2 = fetch("/api/v2/graphs/systems/" + SYSTEM).getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(tagV2).startsWith("\"sha256:").isNotEqualTo(tagV1);
    }

    @Test
    void anOperationNamedByTheCaller_isAnHttpOperationWithTheCalledHostAsProvider() throws Exception {
        // given: a reaction calling a service that names its operation, and one that does not
        observe(new TestReaction(POST_ORDER, List.of(PUT_STOCK, POST_TO_HOST), "placeOrder"), "order-service");
        aggregationService.aggregateData(getToday());
        scheduledTasksService.refreshReactionGraphInternal();

        // then: both calls are HTTP operations of the graph, and actions of the reaction
        JsonNode graph = getJson("/api/v2/graphs").get("graph");
        assertThat(interfaces(graph)).containsExactlyInAnyOrder(
                "HTTP_OPERATION http_request order-service POST /api/orders",
                "HTTP_OPERATION http_request stock-service PUT /api/stock/{}",
                "HTTP_OPERATION http_request //foo.bar.admin.ch POST /{*}");
        assertThat(edgesByMeaning(graph, namesById(graph))).containsExactlyInAnyOrder(
                "TRIGGER order-service POST /api/orders -> order-service",
                "ACTION order-service -> stock-service PUT /api/stock/{}",
                "ACTION order-service -> //foo.bar.admin.ch POST /{*}");

        // and: the provider of the operation the caller named is the called host, marked with //
        JsonNode postToHost = nodeWithFqn(graph, POST_TO_HOST.fqn());
        assertThat(postToHost.get("provider").asString()).isEqualTo("//foo.bar.admin.ch");
        assertThat(postToHost.get("method").asString()).isEqualTo("POST");
        assertThat(postToHost.get("path").asString()).isEqualTo("/{*}");

        // and: it has a graph of its own, with the path from the index
        JsonNode entry = getJson("/api/v2/graphs/interfaces?interfaceType=http_request").get("entries").valueStream()
                .filter(candidate -> candidate.get("fqn").asString().equals(POST_TO_HOST.fqn()))
                .findFirst().orElseThrow();
        JsonNode graphOfTheOperation = getJson(entry.get("path").asString().substring(contextPath.length()))
                .get("graph");
        assertThat(nodesOfType(graphOfTheOperation, "REACTION")).hasSize(1);
    }

    @Test
    void theGraphOfAnHttpOperation_isFetchedWithThePathFromTheIndex() throws Exception {
        observe(new TestReaction(POST_ORDER, List.of(ORDER_CREATED, PUT_STOCK), "placeOrder"), "order-service");
        observe(new TestReaction(PUT_STOCK, List.of(STOCK_CHANGED), "changeStock"), "stock-service");
        observe(new TestReaction(STOCK_CHANGED, List.of(SEND_REPORT), "reportStock"), "report-service");
        aggregationService.aggregateData(getToday());
        scheduledTasksService.refreshReactionGraphInternal();

        // when: the index is asked for the HTTP operations
        JsonNode index = getJson("/api/v2/graphs/interfaces?interfaceType=http_request");

        // then: it lists both, and no other type
        assertThat(index.get("entries")).hasSize(2).allSatisfy(entry ->
                assertThat(entry.get("interfaceType").asString()).isEqualTo("http_request"));
        assertThat(getJson("/api/v2/graphs/interfaces").get("entries")).hasSize(5);
        JsonNode entry = index.get("entries").valueStream()
                .filter(candidate -> candidate.get("fqn").asString().equals(PUT_STOCK.fqn()))
                .findFirst().orElseThrow();
        assertThat(entry.get("path").asString())
                .isEqualTo(contextPath + "/api/v2/graphs/interfaces/" + entry.get("id").asString());

        // and: its path leads to the graph of the operation, tagged as the entry says
        MvcResult response = fetch(entry.get("path").asString().substring(contextPath.length()));
        assertThat(response.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo(entry.get("etag").asString());
        JsonNode graph = jsonMapper.readTree(response.getResponse().getContentAsString()).get("graph");

        // and: the graph has the two reactions of the operation whole - the one calling it with its REST trigger
        // and its message action - and not the reaction that only reacts to the neighbouring message
        assertThat(edgesByMeaning(graph, namesById(graph))).containsExactlyInAnyOrder(
                "TRIGGER order-service POST /api/orders -> order-service",
                "ACTION order-service -> OrderCreated",
                "ACTION order-service -> stock-service PUT /api/stock/{}",
                "TRIGGER stock-service PUT /api/stock/{} -> stock-service",
                "ACTION stock-service -> StockChanged");
        assertThat(nodesOfType(graph, "REACTION")).hasSize(2);

        // and: the graph is not sent again to a consumer that has it
        mvc.perform(get(entry.get("path").asString().substring(contextPath.length()))
                        .header(HttpHeaders.IF_NONE_MATCH, entry.get("etag").asString())
                        .with(authentication(readRole())))
                .andExpect(status().isNotModified());
    }

    /**
     * The contract for consumers allows new kinds of nodes and edges, and a client generated from the OpenAPI
     * document has to honour it: the kinds of node known today are classes of their own, and a node of a kind added
     * later is read as an interface instead of being rejected. The document says so with the discriminator mapping
     * and the default implementation the OpenAPI generator takes from it.
     */
    @Test
    void theOpenApiDocumentOfTheV2_describesTheKindsOfNode_andWhatANodeOfAnUnknownKindIsReadAs() throws Exception {
        JsonNode openApi = getJson("/api-docs/reaction-observer-service-api-v2");

        assertThat(openApi.get("paths").propertyNames()).containsExactlyInAnyOrder(
                "/api/v2/graphs", "/api/v2/graphs/systems", "/api/v2/graphs/systems/{systemName}",
                "/api/v2/graphs/components", "/api/v2/graphs/components/{componentName}",
                "/api/v2/graphs/interfaces", "/api/v2/graphs/interfaces/{interfaceId}");
        JsonNode schemas = openApi.get("components").get("schemas");
        JsonNode node = schemas.get("NodeV2Dto");

        // the nodes of a graph are nodes, not one of the kinds known today
        JsonNode nodes = schemas.get("GraphV2Dto").get("properties").get("nodes").get("items");
        assertThat(nodes.get("$ref").asString()).isEqualTo(SCHEMAS + "NodeV2Dto");
        assertThat(nodes.has("oneOf")).isFalse();

        // and: every known nodeType is mapped to the schema of its kind
        JsonNode discriminator = node.get("discriminator");
        assertThat(discriminator.get("propertyName").asString()).isEqualTo("nodeType");
        Map<String, String> kinds = Map.of("REACTION", "ReactionNodeV2Dto", "INTERFACE", "InterfaceNodeV2Dto",
                "MESSAGE", "MessageNodeV2Dto", "HTTP_OPERATION", "HttpOperationNodeV2Dto");
        assertThat(discriminator.get("mapping").propertyNames()).containsExactlyInAnyOrderElementsOf(kinds.keySet());
        kinds.forEach((nodeType, schema) -> {
            assertThat(discriminator.get("mapping").get(nodeType).asString()).isEqualTo(SCHEMAS + schema);
            assertThat(schemas.has(schema)).isTrue();
        });

        // and: a node with any other nodeType is read as an interface
        assertThat(node.get("x-jackson-default-impl").asString()).isEqualTo("InterfaceNodeV2Dto");

        // and: the schemas extend each other as the kinds do, each with the fields of its kind only
        assertThat(parentOf(schemas.get("ReactionNodeV2Dto"))).isEqualTo(SCHEMAS + "NodeV2Dto");
        assertThat(parentOf(schemas.get("InterfaceNodeV2Dto"))).isEqualTo(SCHEMAS + "NodeV2Dto");
        assertThat(parentOf(schemas.get("MessageNodeV2Dto"))).isEqualTo(SCHEMAS + "InterfaceNodeV2Dto");
        assertThat(parentOf(schemas.get("HttpOperationNodeV2Dto"))).isEqualTo(SCHEMAS + "InterfaceNodeV2Dto");
        assertThat(node.get("properties").propertyNames()).containsExactlyInAnyOrder("id", "nodeType");
        assertThat(ownProperties(schemas.get("ReactionNodeV2Dto")).propertyNames()).containsExactlyInAnyOrder(
                "component", "system", "median", "status", "reactionModelVersion");
        assertThat(ownProperties(schemas.get("InterfaceNodeV2Dto")).propertyNames())
                .containsExactlyInAnyOrder("interfaceType", "fqn");
        assertThat(ownProperties(schemas.get("MessageNodeV2Dto")).propertyNames())
                .containsExactlyInAnyOrder("messageType", "variant");
        assertThat(ownProperties(schemas.get("HttpOperationNodeV2Dto")).propertyNames())
                .containsExactlyInAnyOrder("provider", "method", "path");

        // and: the values that can be added to are texts, not enumerations
        JsonNode edge = schemas.get("EdgeV2Dto");
        for (JsonNode openValue : List.of(node.get("properties").get("nodeType"),
                ownProperties(schemas.get("ReactionNodeV2Dto")).get("status"),
                ownProperties(schemas.get("InterfaceNodeV2Dto")).get("interfaceType"),
                edge.get("properties").get("edgeType"))) {
            assertThat(openValue.get("type").asString()).isEqualTo("string");
            assertThat(openValue.has("enum")).isFalse();
        }
        assertThat(edge.has("oneOf")).isFalse();
        assertThat(edge.has("discriminator")).isFalse();

        // and: the fields that are null when they have no value are described as such
        assertThat(typesOf(ownProperties(schemas.get("MessageNodeV2Dto")).get("variant")))
                .containsExactlyInAnyOrder("string", "null");
        assertThat(typesOf(schemas.get("GraphIndexEntryDto").get("properties").get("system")))
                .containsExactlyInAnyOrder("string", "null");

        // and: a reaction always has its median, a number
        assertThat(typesOf(ownProperties(schemas.get("ReactionNodeV2Dto")).get("median"))).containsExactly("integer");
        assertThat(schemas.get("ReactionNodeV2Dto").get("required").valueStream().map(JsonNode::asString))
                .contains("median");

        // and: Swagger UI can log in for the v2, with the scheme the jEAP swagger starter registers
        assertThat(openApi.get("components").get("securitySchemes").get("OIDC").get("type").asString())
                .isEqualTo("openIdConnect");
        openApi.get("paths").forEach(path -> path.forEach(operation ->
                assertThat(operation.get("security")).anyMatch(requirement -> requirement.has("OIDC"))));

        // and: the group of the v1 is what it was, without the v2, and each group has the version of its API
        JsonNode openApiV1 = getJson("/api-docs/reaction-observer-service-api");
        assertThat(openApi.get("info").get("version").asString()).isEqualTo("2.0.0");
        assertThat(openApiV1.get("info").get("version").asString()).isEqualTo("1.0.0");
        assertThat(openApiV1.get("paths").propertyNames())
                .contains("/api/graphs", "/api/graphs/messages/{messageType}", "/api/statistics/last-observation-date")
                .noneMatch(path -> path.startsWith("/api/v2"));
    }

    /** The schema a schema extends: the reference in its allOf. */
    private static String parentOf(JsonNode schema) {
        for (JsonNode part : schema.get("allOf")) {
            if (part.has("$ref")) {
                return part.get("$ref").asString();
            }
        }
        throw new AssertionError("extends nothing: " + schema);
    }

    /** The types a property can have: one, or several when it can be null. */
    private static List<String> typesOf(JsonNode property) {
        JsonNode type = property.get("type");
        return type.isArray() ? type.valueStream().map(JsonNode::asString).toList() : List.of(type.asString());
    }

    /** The properties a schema adds to the one it extends. */
    private static JsonNode ownProperties(JsonNode schema) {
        for (JsonNode part : schema.get("allOf")) {
            if (part.has("properties")) {
                return part.get("properties");
            }
        }
        throw new AssertionError("adds no properties: " + schema);
    }

    private void observe(TestReaction reaction, String component) {
        sendAndAwaitReactionPersistence(reaction, SYSTEM, component);
        sendAndAwaitObservedEventForReaction(reaction, SYSTEM, component, 3);
    }

    private MvcResult fetch(String path) throws Exception {
        return mvc.perform(get(path).with(authentication(readRole())))
                .andExpect(status().isOk())
                .andReturn();
    }

    private JsonNode getJson(String path) throws Exception {
        return jsonMapper.readTree(fetch(path).getResponse().getContentAsString());
    }

    private static List<JsonNode> nodesOfType(JsonNode graph, String nodeType) {
        List<JsonNode> nodes = new ArrayList<>();
        graph.get("nodes").forEach(node -> {
            if (nodeType.equals(node.get("nodeType").asString())) {
                nodes.add(node);
            }
        });
        return nodes;
    }

    private static JsonNode nodeWithFqn(JsonNode graph, String fqn) {
        return graph.get("nodes").valueStream()
                .filter(node -> node.has("fqn") && fqn.equals(node.get("fqn").asString()))
                .findFirst().orElseThrow();
    }

    /** Every interface node as its node type, interface type and FQN. */
    private static List<String> interfaces(JsonNode graph) {
        return graph.get("nodes").valueStream()
                .filter(node -> node.has("interfaceType"))
                .map(node -> node.get("nodeType").asString() + " " + node.get("interfaceType").asString() + " "
                             + node.get("fqn").asString())
                .toList();
    }

    private static List<String> names(JsonNode index) {
        return index.get("entries").valueStream().map(entry -> entry.get("name").asString()).toList();
    }

    /** The FQN of an interface or the component of a reaction, by the id of the node. */
    private static Map<String, String> namesById(JsonNode graph) {
        Map<String, String> names = new HashMap<>();
        graph.get("nodes").forEach(node -> names.put(node.get("id").asString(),
                node.has("fqn") ? node.get("fqn").asString() : node.get("component").asString()));
        return names;
    }

    private static List<String> edgesByMeaning(JsonNode graph, Map<String, String> namesById) {
        List<String> edges = new ArrayList<>();
        graph.get("edges").forEach(edge -> {
            assertThat(edge.propertyNames()).containsExactlyInAnyOrder("edgeType", "sourceId", "targetId");
            assertThat(namesById).containsKeys(edge.get("sourceId").asString(), edge.get("targetId").asString());
            edges.add(edge.get("edgeType").asString() + " " + namesById.get(edge.get("sourceId").asString())
                      + " -> " + namesById.get(edge.get("targetId").asString()));
        });
        return edges;
    }

    private static JeapAuthenticationToken readRole() {
        return JeapAuthenticationTestTokenBuilder.create().withUserRoles(READ_ROLE).build();
    }
}
