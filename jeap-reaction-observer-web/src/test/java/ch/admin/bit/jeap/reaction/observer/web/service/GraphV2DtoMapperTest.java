package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Action;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.web.GraphV2TestGraph;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.EdgeV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.GraphV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.HttpOperationNodeV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.InterfaceNodeV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.MessageNodeV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.NodeV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.ReactionNodeV2Dto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ch.admin.bit.jeap.reaction.observer.web.GraphV2TestGraph.CHECK_ORDER;
import static ch.admin.bit.jeap.reaction.observer.web.GraphV2TestGraph.RESERVE_CREDIT;
import static org.assertj.core.api.Assertions.assertThat;

class GraphV2DtoMapperTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final GraphV2Dto graph = GraphV2DtoMapper.map(GraphV2TestGraph.graph());

    @Test
    void map_aMessage_isAMessageNodeWithItsTypeAndFqn() {
        assertThat(node("I12")).isEqualTo(MessageNodeV2Dto.builder()
                .id("I12").interfaceType("event").fqn("OrderChecked/eu")
                .messageType("OrderChecked").variant("eu").build());
        assertThat(node("I12").getNodeType()).isEqualTo("MESSAGE");
    }

    @Test
    void map_anHttpOperation_isAnHttpOperationNodeWithItsTypeAndFqn() {
        assertThat(node("I14")).isEqualTo(HttpOperationNodeV2Dto.builder()
                .id("I14").interfaceType("http_request")
                .fqn("partner-service POST /api/credits")
                .provider("partner-service").method("POST").path("/api/credits").build());
        assertThat(node("I14").getNodeType()).isEqualTo("HTTP_OPERATION");
    }

    @Test
    void map_anInterfaceOfAnyOtherType_isAGenericInterfaceNodeWithItsTypeAndFqn() {
        assertThat(node("I16")).isEqualTo(InterfaceNodeV2Dto.builder()
                .id("I16").interfaceType("scheduled").fqn("nightly-job").build());
        assertThat(node("I16").getNodeType()).isEqualTo("INTERFACE");
    }

    @Test
    void map_aReaction_hasItsComponentSystemAndMedian_andTheNeutralValuesOfTheFieldsNotDerivedYet() {
        assertThat(node("R11")).isEqualTo(ReactionNodeV2Dto.builder()
                .id("R11").component("order-service").system("shop").median(7)
                .status("ACTIVE").reactionModelVersion(1).build());
        assertThat(node("R11").getNodeType()).isEqualTo("REACTION");
    }

    @Test
    void map_theIdsAreUniqueInTheGraph_althoughInterfacesAndReactionsShareDatabaseIds() {
        List<String> ids = graph.nodes().stream().map(NodeV2Dto::getId).toList();

        assertThat(ids).doesNotHaveDuplicates()
                .contains("I11", "R11", "I12", "R12", "I13", "R13");
    }

    @Test
    void map_everyEdgeIsItsTypeAndTheIdsOfItsEnds_andPointsToNodesOfTheGraph() {
        Set<String> ids = graph.nodes().stream().map(NodeV2Dto::getId).collect(Collectors.toSet());

        assertThat(graph.edges()).hasSize(8).allSatisfy(edge ->
                assertThat(ids).contains(edge.sourceId(), edge.targetId()));
        assertThat(graph.edges()).contains(
                new EdgeV2Dto("TRIGGER", "I11", "R11"),
                new EdgeV2Dto("ACTION", "R11", "I14"),
                new EdgeV2Dto("TRIGGER", "I14", "R12"));
    }

    @Test
    void map_twoEqualEdges_areOne() {
        Graph domainGraph = GraphV2TestGraph.graph();
        List<ch.admin.bit.jeap.reaction.observer.domain.models.graph.Edge> edges =
                new java.util.ArrayList<>(domainGraph.edges());
        edges.add(Action.builder().source(CHECK_ORDER).target(RESERVE_CREDIT).build());

        GraphV2Dto mapped = GraphV2DtoMapper.map(new Graph(domainGraph.nodes(), edges));

        assertThat(mapped.edges()).hasSize(8).doesNotHaveDuplicates();
    }

    /**
     * Every kind of node has the fields of its kind and no other: a reaction is not an interface, so it has
     * neither a type nor an FQN. A field without a value is written as null.
     */
    @Test
    void aNode_isWrittenWithTheFieldsOfItsKindOnly() {
        JsonNode nodes = jsonMapper.valueToTree(graph).get("nodes");

        assertThat(written(nodes, "R13").propertyNames()).containsExactlyInAnyOrder("nodeType", "id", "component",
                "system", "median", "status", "reactionModelVersion");
        assertThat(written(nodes, "I11").propertyNames()).containsExactlyInAnyOrder("nodeType", "id",
                "interfaceType", "fqn", "messageType", "variant");
        assertThat(written(nodes, "I11").get("variant").isNull()).isTrue();
        assertThat(written(nodes, "I14").propertyNames()).containsExactlyInAnyOrder("nodeType", "id",
                "interfaceType", "fqn", "provider", "method", "path");
        assertThat(written(nodes, "I16").propertyNames()).containsExactlyInAnyOrder("nodeType", "id",
                "interfaceType", "fqn");
    }

    /** The kind of a node is written once, as the nodeType, whether the node is part of a graph or on its own. */
    @Test
    void aNode_isWrittenWithItsNodeType_alsoOnItsOwn() {
        JsonNode nodes = jsonMapper.valueToTree(graph).get("nodes");

        assertThat(written(nodes, "R13").get("nodeType").asString()).isEqualTo("REACTION");
        assertThat(written(nodes, "I11").get("nodeType").asString()).isEqualTo("MESSAGE");
        assertThat(written(nodes, "I14").get("nodeType").asString()).isEqualTo("HTTP_OPERATION");
        assertThat(written(nodes, "I16").get("nodeType").asString()).isEqualTo("INTERFACE");
        assertThat(jsonMapper.valueToTree(node("I11")).get("nodeType").asString()).isEqualTo("MESSAGE");
    }

    private static JsonNode written(JsonNode nodes, String id) {
        for (JsonNode node : nodes) {
            if (id.equals(node.get("id").asString())) {
                return node;
            }
        }
        throw new AssertionError("no node " + id);
    }

    private NodeV2Dto node(String id) {
        return graph.nodes().stream().filter(node -> node.getId().equals(id)).findFirst().orElseThrow();
    }
}
