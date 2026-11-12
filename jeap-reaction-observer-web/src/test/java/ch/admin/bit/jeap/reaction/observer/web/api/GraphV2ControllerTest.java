package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshot;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshotFactory;
import ch.admin.bit.jeap.reaction.observer.web.GraphV2TestGraph;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.web.config.WebSecurityConfig;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import ch.admin.bit.jeap.security.resource.configuration.MvcSecurityConfiguration;
import ch.admin.bit.jeap.security.resource.properties.ResourceServerProperties;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import ch.admin.bit.jeap.security.resource.token.TokenConfiguration;
import ch.admin.bit.jeap.security.test.resource.JeapAuthenticationTestTokenBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The graphs of the API v2, served from a graph with every kind of interface - see {@link GraphV2TestGraph}.
 */
@WebMvcTest(GraphV2Controller.class)
@AutoConfigureMockMvc
@Import({WebSecurityConfig.class, ReactionObserverProperties.class, EtagSupport.class, GraphExtractor.class,
        GraphFingerprintCalculator.class})
// The resource server is required, so a slice needs the starter's security configuration - see GraphControllerTest
@ImportAutoConfiguration({ResourceServerProperties.class, TokenConfiguration.class,
        MvcSecurityConfiguration.class})
@EnableWebSecurity
class GraphV2ControllerTest {

    private static final String READ_ROLE = "reactionobserver_@reactions_#read";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private GraphHolder graphHolder;

    private GraphSnapshot snapshot;

    @BeforeEach
    void setUp() {
        snapshot = new GraphSnapshotFactory(new GraphExtractor(),
                new GraphFingerprintCalculator(JsonMapper.builder().build()),
                new EtagSupport(JsonMapper.builder().build()), "").of(GraphV2TestGraph.graph());
        when(graphHolder.getSnapshot()).thenReturn(snapshot);
    }

    @Test
    void theGraph_hasEveryKindOfInterface_andIsTaggedWithItsFingerprint() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v2/graphs").with(authentication(readRoleAuthentication())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"sha256:" + snapshot.v2().graphFingerprint() + "\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andReturn();
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());

        assertThat(body.get("fingerprint").asString()).isEqualTo(snapshot.v2().graphFingerprint());
        assertThat(nodeIds(body)).containsExactlyInAnyOrder("I11", "I12", "I13", "I14", "I15", "I16", "R11", "R12",
                "R13");
        assertThat(node(body, "I14")).isEqualTo(jsonMapper.readTree("""
                {"nodeType":"HTTP_OPERATION","id":"I14","interfaceType":"http_request",\
                "fqn":"partner-service POST /api/credits","provider":"partner-service","method":"POST",\
                "path":"/api/credits"}"""));
        assertThat(node(body, "I12")).isEqualTo(jsonMapper.readTree("""
                {"nodeType":"MESSAGE","id":"I12","interfaceType":"event","fqn":"OrderChecked/eu",\
                "messageType":"OrderChecked","variant":"eu"}"""));
        assertThat(node(body, "I11")).isEqualTo(jsonMapper.readTree("""
                {"nodeType":"MESSAGE","id":"I11","interfaceType":"event","fqn":"OrderSubmitted",\
                "messageType":"OrderSubmitted","variant":null}"""));
        assertThat(node(body, "I16")).isEqualTo(jsonMapper.readTree("""
                {"nodeType":"INTERFACE","id":"I16","interfaceType":"scheduled","fqn":"nightly-job"}"""));
        assertThat(node(body, "R11")).isEqualTo(jsonMapper.readTree("""
                {"nodeType":"REACTION","id":"R11","component":"order-service","system":"shop","median":7,\
                "status":"ACTIVE","reactionModelVersion":1}"""));
        assertThat(edges(body)).containsExactlyInAnyOrder(
                "TRIGGER I11 R11", "ACTION R11 I12", "ACTION R11 I14", "ACTION R11 I16",
                "TRIGGER I14 R12", "ACTION R12 I13", "TRIGGER I15 R13", "ACTION R13 I11");
        // An edge is its type and its two ends, nothing else
        body.get("graph").get("edges").forEach(edge ->
                assertThat(edge.propertyNames()).containsExactlyInAnyOrder("edgeType", "sourceId", "targetId"));
    }

    @Test
    void theGraphOfASystem_isFoundIgnoringCase_andTaggedWithItsFingerprint() throws Exception {
        JsonNode body = getJson("/api/v2/graphs/systems/Shop",
                "\"sha256:" + snapshot.v2().fingerprintOfSystem("shop") + "\"");

        assertThat(nodeIds(body)).hasSize(9);
    }

    @Test
    void theGraphOfAComponent_hasItsReactionsWhole() throws Exception {
        JsonNode body = getJson("/api/v2/graphs/components/partner-service",
                "\"sha256:" + snapshot.v2().fingerprintOfComponent("partner-service") + "\"");

        assertThat(nodeIds(body)).containsExactlyInAnyOrder("R12", "I14", "I13");
        assertThat(edges(body)).containsExactlyInAnyOrder("TRIGGER I14 R12", "ACTION R12 I13");
    }

    /**
     * The reaction calling the operation with its trigger and all its actions, and the reaction the operation
     * triggers - but not the reaction that is only connected to the neighbouring message I11.
     */
    @Test
    void theGraphOfAnHttpOperation_hasEveryReactionOfItWhole_andNoneOfItsNeighbours() throws Exception {
        JsonNode body = getJson("/api/v2/graphs/interfaces/I14",
                "\"sha256:" + snapshot.v2().fingerprintOfInterface(14) + "\"");

        assertThat(nodeIds(body)).containsExactlyInAnyOrder("I14", "R11", "I11", "I12", "I16", "R12", "I13");
        assertThat(edges(body)).containsExactlyInAnyOrder("TRIGGER I11 R11", "ACTION R11 I12", "ACTION R11 I14",
                "ACTION R11 I16", "TRIGGER I14 R12", "ACTION R12 I13");
    }

    @Test
    void theGraphOfAMessage_hasItsReactionsWithTheirInterfacesOfOtherKinds() throws Exception {
        JsonNode body = getJson("/api/v2/graphs/interfaces/I11",
                "\"sha256:" + snapshot.v2().fingerprintOfInterface(11) + "\"");

        assertThat(nodeIds(body)).containsExactlyInAnyOrder("I11", "R13", "I15", "R11", "I12", "I14", "I16");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v2/graphs", "/api/v2/graphs/systems/shop", "/api/v2/graphs/components/order-service",
            "/api/v2/graphs/interfaces/I14"})
    void aGraph_askedWithItsOwnEntityTag_isNotModified(String resource) throws Exception {
        String etag = mockMvc.perform(get(resource).with(authentication(readRoleAuthentication())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).startsWith("\"sha256:");

        mockMvc.perform(get(resource).header(HttpHeaders.IF_NONE_MATCH, etag)
                        .with(authentication(readRoleAuthentication())))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
        mockMvc.perform(get(resource).header(HttpHeaders.IF_NONE_MATCH, "\"sha256:what-it-had-before\"")
                        .with(authentication(readRoleAuthentication())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    /** R11 is a reaction, 14 lacks the prefix, I99 is no interface of the graph - and i14 is not the id I14. */
    @ParameterizedTest
    @ValueSource(strings = {"/api/v2/graphs/systems/no-such-system", "/api/v2/graphs/components/no-such-component",
            "/api/v2/graphs/interfaces/I99", "/api/v2/graphs/interfaces/R11", "/api/v2/graphs/interfaces/14",
            "/api/v2/graphs/interfaces/i14", "/api/v2/graphs/interfaces/I1234567890123456789"})
    void aGraphThatDoesNotExist_isNotFound(String resource) throws Exception {
        mockMvc.perform(get(resource).with(authentication(readRoleAuthentication())))
                .andExpect(status().isNotFound());
    }

    @Test
    void theGraph_beforeTheFirstRefresh_isEmpty() throws Exception {
        when(graphHolder.getSnapshot()).thenReturn(GraphSnapshot.empty());

        JsonNode body = jsonMapper.readTree(mockMvc.perform(get("/api/v2/graphs")
                        .with(authentication(readRoleAuthentication())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(body.get("graph").get("nodes")).isEmpty();
        assertThat(body.get("fingerprint").asString()).hasSize(64);
    }

    @Test
    void aGraph_withoutTheReadRole_isForbidden_andWithoutCredentialsUnauthorized() throws Exception {
        JeapAuthenticationToken withoutRole = JeapAuthenticationTestTokenBuilder.create().build();

        mockMvc.perform(get("/api/v2/graphs").with(authentication(withoutRole)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/graphs/interfaces/I14"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode getJson(String resource, String expectedEntityTag) throws Exception {
        MvcResult result = mockMvc.perform(get(resource).with(authentication(readRoleAuthentication())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, expectedEntityTag))
                .andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> nodeIds(JsonNode body) {
        List<String> ids = new ArrayList<>();
        body.get("graph").get("nodes").forEach(node -> ids.add(node.get("id").asString()));
        return ids;
    }

    private static JsonNode node(JsonNode body, String id) {
        for (JsonNode node : body.get("graph").get("nodes")) {
            if (id.equals(node.get("id").asString())) {
                return node;
            }
        }
        throw new AssertionError("No node " + id);
    }

    private static List<String> edges(JsonNode body) {
        List<String> edges = new ArrayList<>();
        body.get("graph").get("edges").forEach(edge -> edges.add(edge.get("edgeType").asString() + " "
                                                                 + edge.get("sourceId").asString() + " "
                                                                 + edge.get("targetId").asString()));
        return edges;
    }

    private static JeapAuthenticationToken readRoleAuthentication() {
        return JeapAuthenticationTestTokenBuilder.create().withUserRoles(READ_ROLE).build();
    }
}
