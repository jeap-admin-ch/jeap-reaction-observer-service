package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.SemanticType;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Trigger;
import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshot;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshotFactory;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionsApiAuthorization;
import ch.admin.bit.jeap.reaction.observer.web.config.WebSecurityConfig;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import ch.admin.bit.jeap.security.test.resource.JeapAuthenticationTestTokenBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The indexes, and the conditional requests they exist for.
 */
@WebMvcTest(GraphIndexController.class)
@AutoConfigureMockMvc
@Import({WebSecurityConfig.class, ReactionObserverProperties.class, ReactionsApiAuthorization.class,
        EtagSupport.class})
@EnableWebSecurity
class GraphIndexControllerTest {

    /** The read user of the test configuration - the basic-auth half of the API's two mechanisms. */
    private static final String READ_USER = "read";
    private static final String READ_PASSWORD = "secret";

    private static final String CONTEXT_PATH = "/jeap-reaction-observer";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GraphHolder graphHolder;

    @BeforeEach
    void setUp() {
        // Built here rather than autowired: the web slice holds the controller and what it is given, and the
        // fingerprint calculator belongs to the refresh rather than to a request
        GraphSnapshotFactory factory = new GraphSnapshotFactory(
                new ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor(),
                new GraphFingerprintCalculator(JsonMapper.builder().build()),
                new EtagSupport(JsonMapper.builder().build()), CONTEXT_PATH);
        when(graphHolder.getSnapshot()).thenReturn(factory.of(aGraph()));
    }

    @Test
    void systemIndex_listsEverySystemWithItsEntityTagAndPath() throws Exception {
        mockMvc.perform(get("/api/graphs/systems").with(httpBasic(READ_USER, READ_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].name").value("orders"))
                .andExpect(jsonPath("$.entries[0].system").doesNotExist())
                .andExpect(jsonPath("$.entries[0].etag").value(org.hamcrest.Matchers.startsWith("\"sha256:")))
                .andExpect(jsonPath("$.entries[0].path").value(CONTEXT_PATH + "/api/graphs/systems/orders"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
    }

    @Test
    void componentIndex_namesTheSystemOfEveryComponent() throws Exception {
        mockMvc.perform(get("/api/graphs/components").with(httpBasic(READ_USER, READ_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].name").value("orders-intake"))
                .andExpect(jsonPath("$.entries[0].system").value("orders"))
                .andExpect(jsonPath("$.entries[0].path")
                        .value(CONTEXT_PATH + "/api/graphs/components/orders-intake"));
    }

    @Test
    void messageIndex_listsTheVariantsOfEveryMessageType() throws Exception {
        mockMvc.perform(get("/api/graphs/messages").with(httpBasic(READ_USER, READ_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].messageType").value("OrdersPaymentAcceptedEvent"))
                .andExpect(jsonPath("$.entries[0].variants.length()").value(1))
                .andExpect(jsonPath("$.entries[0].variants[0]").value("OrdersPaymentAcceptedEvent"))
                .andExpect(jsonPath("$.entries[0].path")
                        .value(CONTEXT_PATH + "/api/graphs/messages/OrdersPaymentAcceptedEvent"));
    }

    /** The whole point: a consumer that already has the index is told so, and gets no payload. */
    @Test
    void anIndex_askedWithItsOwnEntityTag_isNotModified() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/graphs/systems").with(httpBasic(READ_USER, READ_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotNull();

        mockMvc.perform(get("/api/graphs/systems")
                        .header(HttpHeaders.IF_NONE_MATCH, etag)
                        .with(httpBasic(READ_USER, READ_PASSWORD)))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
    }

    @Test
    void anIndex_askedWithAnotherEntityTag_isAnsweredWithTheIndex() throws Exception {
        mockMvc.perform(get("/api/graphs/systems")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"sha256:something-else\"")
                        .with(httpBasic(READ_USER, READ_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1));
    }

    @Test
    void anIndex_ofAnEmptyGraph_isEmptyRatherThanAFailure() throws Exception {
        when(graphHolder.getSnapshot()).thenReturn(GraphSnapshot.empty());

        mockMvc.perform(get("/api/graphs/systems").with(httpBasic(READ_USER, READ_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0));
    }

    @Test
    void anIndex_withoutTheReadRole_isForbidden() throws Exception {
        JeapAuthenticationToken withoutRole = JeapAuthenticationTestTokenBuilder.create().build();

        mockMvc.perform(get("/api/graphs/systems").with(authentication(withoutRole)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anIndex_withoutAnyCredentials_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/graphs/systems"))
                .andExpect(status().isUnauthorized());
    }

    private static Graph aGraph() {
        Message message = Message.builder().id(1).messageType("OrdersPaymentAcceptedEvent")
                .semantic(SemanticType.EVENT).build();
        Reaction reaction = Reaction.builder().id(2).component("orders-intake").system("orders").build();
        return new Graph(List.of(message, reaction),
                List.of(Trigger.builder().source(message).target(reaction).median(4).build()));
    }
}
