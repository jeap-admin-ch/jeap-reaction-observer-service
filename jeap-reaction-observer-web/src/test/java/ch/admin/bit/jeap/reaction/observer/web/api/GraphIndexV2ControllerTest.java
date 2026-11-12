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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The indexes of the API v2, and the graphs their paths lead to - see {@link GraphV2TestGraph}.
 */
@WebMvcTest({GraphIndexV2Controller.class, GraphV2Controller.class})
@AutoConfigureMockMvc
@Import({WebSecurityConfig.class, ReactionObserverProperties.class, EtagSupport.class, GraphExtractor.class,
        GraphFingerprintCalculator.class})
// The resource server is required, so a slice needs the starter's security configuration - see GraphControllerTest
@ImportAutoConfiguration({ResourceServerProperties.class, TokenConfiguration.class,
        MvcSecurityConfiguration.class})
@EnableWebSecurity
class GraphIndexV2ControllerTest {

    private static final String READ_ROLE = "reactionobserver_@reactions_#read";

    private static final String CONTEXT_PATH = "/jeap-reaction-observer";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private GraphHolder graphHolder;

    @BeforeEach
    void setUp() {
        GraphSnapshotFactory factory = new GraphSnapshotFactory(new GraphExtractor(),
                new GraphFingerprintCalculator(JsonMapper.builder().build()),
                new EtagSupport(JsonMapper.builder().build()), CONTEXT_PATH);
        when(graphHolder.getSnapshot()).thenReturn(factory.of(GraphV2TestGraph.graph()));
    }

    @Test
    void systemIndex_listsEverySystemWithItsEntityTagAndPath() throws Exception {
        mockMvc.perform(get("/api/v2/graphs/systems").with(authentication(readRoleAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].name").value("shop"))
                .andExpect(jsonPath("$.entries[0].etag").value(org.hamcrest.Matchers.startsWith("\"sha256:")))
                .andExpect(jsonPath("$.entries[0].path").value(CONTEXT_PATH + "/api/v2/graphs/systems/shop"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
    }

    @Test
    void componentIndex_namesTheSystemOfEveryComponent() throws Exception {
        mockMvc.perform(get("/api/v2/graphs/components").with(authentication(readRoleAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].name").value("order-service"))
                .andExpect(jsonPath("$.entries[0].system").value("shop"))
                .andExpect(jsonPath("$.entries[1].name").value("partner-service"))
                .andExpect(jsonPath("$.entries[1].path")
                        .value(CONTEXT_PATH + "/api/v2/graphs/components/partner-service"));
    }

    @Test
    void interfaceIndex_listsTheInterfacesOfEveryType() throws Exception {
        mockMvc.perform(get("/api/v2/graphs/interfaces").with(authentication(readRoleAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(6))
                .andExpect(jsonPath("$.entries[0].id").value("I13"))
                .andExpect(jsonPath("$.entries[0].interfaceType").value("event"))
                .andExpect(jsonPath("$.entries[0].fqn").value("CreditReserved"))
                .andExpect(jsonPath("$.entries[0].etag").value(org.hamcrest.Matchers.startsWith("\"sha256:")))
                .andExpect(jsonPath("$.entries[0].path").value(CONTEXT_PATH + "/api/v2/graphs/interfaces/I13"))
                .andExpect(jsonPath("$.entries[1].fqn").value("OrderChecked/eu"))
                .andExpect(jsonPath("$.entries[4].interfaceType").value("http_request"))
                .andExpect(jsonPath("$.entries[4].fqn").value("partner-service POST /api/credits"))
                .andExpect(jsonPath("$.entries[5].interfaceType").value("scheduled"));
    }

    @Test
    void interfaceIndex_canBeRestrictedToOneInterfaceType() throws Exception {
        mockMvc.perform(get("/api/v2/graphs/interfaces").queryParam("interfaceType", "http_request")
                        .with(authentication(readRoleAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].id").value("I15"))
                .andExpect(jsonPath("$.entries[1].id").value("I14"));
    }

    /** Not an error: the graph has no interface of that type, and that emptiness has a tag like any other index. */
    @Test
    void interfaceIndex_ofATypeWithoutInterfaces_isEmpty_andCanBeAskedConditionally() throws Exception {
        String etag = mockMvc.perform(get("/api/v2/graphs/interfaces").queryParam("interfaceType", "unknown")
                        .with(authentication(readRoleAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotNull();

        mockMvc.perform(get("/api/v2/graphs/interfaces").queryParam("interfaceType", "unknown")
                        .header(HttpHeaders.IF_NONE_MATCH, etag)
                        .with(authentication(readRoleAuthentication())))
                .andExpect(status().isNotModified());
    }

    /** What the index is for: every path it names is a graph, answered with the tag the entry carries. */
    @Test
    void everyPathOfTheInterfaceIndex_leadsToTheGraphOfTheInterface_withTheEntityTagOfTheEntry() throws Exception {
        JsonNode index = jsonMapper.readTree(mockMvc.perform(get("/api/v2/graphs/interfaces")
                        .with(authentication(readRoleAuthentication())))
                .andReturn().getResponse().getContentAsString());

        assertThat(index.get("entries")).hasSize(6);
        for (JsonNode entry : index.get("entries")) {
            String path = entry.get("path").asString();
            assertThat(path).startsWith(CONTEXT_PATH);
            MvcResult graph = mockMvc.perform(get(path.substring(CONTEXT_PATH.length()))
                            .with(authentication(readRoleAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ETAG, entry.get("etag").asString()))
                    .andReturn();
            assertThat(graph.getResponse().getContentAsString())
                    .contains("\"id\":\"" + entry.get("id").asString() + "\"");
        }
    }

    @Test
    void anIndex_askedWithItsOwnEntityTag_isNotModified() throws Exception {
        String etag = mockMvc.perform(get("/api/v2/graphs/interfaces")
                        .with(authentication(readRoleAuthentication())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotNull();

        mockMvc.perform(get("/api/v2/graphs/interfaces")
                        .header(HttpHeaders.IF_NONE_MATCH, etag)
                        .with(authentication(readRoleAuthentication())))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
    }

    @Test
    void anIndex_beforeTheFirstRefresh_isEmptyRatherThanAFailure() throws Exception {
        when(graphHolder.getSnapshot()).thenReturn(GraphSnapshot.empty());

        for (String index : new String[]{"/api/v2/graphs/systems", "/api/v2/graphs/components",
                "/api/v2/graphs/interfaces", "/api/v2/graphs/interfaces?interfaceType=event"}) {
            mockMvc.perform(get(index).with(authentication(readRoleAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                    .andExpect(jsonPath("$.entries.length()").value(0));
        }
    }

    @Test
    void anIndex_withoutTheReadRole_isForbidden_andWithoutCredentialsUnauthorized() throws Exception {
        JeapAuthenticationToken withoutRole = JeapAuthenticationTestTokenBuilder.create().build();

        mockMvc.perform(get("/api/v2/graphs/interfaces").with(authentication(withoutRole)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/graphs/interfaces"))
                .andExpect(status().isUnauthorized());
    }

    private static JeapAuthenticationToken readRoleAuthentication() {
        return JeapAuthenticationTestTokenBuilder.create().withUserRoles(READ_ROLE).build();
    }
}
