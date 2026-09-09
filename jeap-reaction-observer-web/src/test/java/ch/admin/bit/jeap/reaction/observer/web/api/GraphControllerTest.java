package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshot;
import tools.jackson.databind.json.JsonMapper;
import ch.admin.bit.jeap.reaction.observer.web.GraphSnapshotFactory;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionsApiAuthorization;
import ch.admin.bit.jeap.reaction.observer.web.config.WebSecurityConfig;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.*;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphDtoMapper;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import ch.admin.bit.jeap.security.test.resource.JeapAuthenticationTestTokenBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GraphController.class)
@AutoConfigureMockMvc
@Import({WebSecurityConfig.class, ReactionObserverProperties.class, ReactionsApiAuthorization.class, EtagSupport.class})
@EnableWebSecurity
class GraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GraphHolder graphHolder;

    @MockitoBean
    private GraphFingerprintCalculator fingerprintCalculator;

    @MockitoBean
    private GraphExtractor graphExtractor;

    @Test
    void testGetAllReactionsGraph() throws Exception {
        Message message = Message.builder()
                .id(1L)
                .messageType("TestType")
                .variant("v1")
                .semantic(SemanticType.EVENT)
                .build();

        Reaction reaction = Reaction.builder()
                .id(2L)
                .component("TestComponent")
                .system("TestSystem")
                .build();

        Trigger trigger = Trigger.builder()
                .source(message)
                .target(reaction)
                .median(5)
                .build();

        Graph domainGraph = new Graph(List.of(message, reaction), List.of(trigger));

        // The fingerprint comes from the snapshot the refresh built, so the test asks it for the value
        // rather than dictating one - see GraphSnapshotFactoryTest for how it is computed
        GraphSnapshot snapshot = aSnapshotOf(domainGraph);
        when(graphHolder.getSnapshot()).thenReturn(snapshot);
        String expectedFingerprint = snapshot.graphFingerprint();

        // Act & Assert
        JeapAuthenticationToken authentication = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("reaction-observer-read")
                .build();
        mockMvc.perform(get("/api/graphs")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(authentication))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graph.nodes[0].id").value(1))
                .andExpect(jsonPath("$.graph.nodes[1].id").value(2))
                .andExpect(jsonPath("$.graph.edges[0].edgeType").value("TRIGGER"))
                .andExpect(jsonPath("$.fingerprint").value(expectedFingerprint));
    }

    @Test
    void testGetSystemRelatedGraph() throws Exception {
        String systemName = "TestSystem";

        Message message = Message.builder()
                .id(1L)
                .messageType("TestType")
                .variant("v1")
                .semantic(SemanticType.EVENT)
                .build();

        Reaction reaction = Reaction.builder()
                .id(2L)
                .component("TestComponent")
                .system(systemName)
                .build();

        Trigger trigger = Trigger.builder()
                .source(message)
                .target(reaction)
                .median(5)
                .build();

        Graph fullGraph = new Graph(List.of(message, reaction), List.of(trigger));

        GraphSnapshot snapshot = aSnapshotOf(fullGraph);
        when(graphHolder.getSnapshot()).thenReturn(snapshot);
        when(graphExtractor.getSystemRelatedGraph(fullGraph, systemName)).thenReturn(fullGraph);

        JeapAuthenticationToken authentication = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("reaction-observer-read")
                .build();

        mockMvc.perform(get("/api/graphs/systems/{systemName}", systemName)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graph.nodes[0].id").value(1))
                .andExpect(jsonPath("$.graph.nodes[1].id").value(2))
                .andExpect(jsonPath("$.graph.edges[0].edgeType").value("TRIGGER"))
                .andExpect(jsonPath("$.fingerprint").value(snapshot.fingerprintOfSystem(systemName)));
    }

    @Test
    void testGetSystemRelatedGraph_notFound() throws Exception {
        String systemName = "UnknownSystem";

        Graph fullGraph = new Graph(List.of(), List.of());

        when(graphHolder.getSnapshot()).thenReturn(aSnapshotOf(fullGraph));
        when(graphExtractor.getSystemRelatedGraph(fullGraph, systemName)).thenReturn(fullGraph);

        JeapAuthenticationToken authentication = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("reaction-observer-read")
                .build();

        mockMvc.perform(get("/api/graphs/systems/{systemName}", systemName)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(authentication)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetComponentRelatedGraph() throws Exception {
        String componentName = "TestComponent";

        Message message = Message.builder()
                .id(1L)
                .messageType("TestType")
                .variant("v1")
                .semantic(SemanticType.EVENT)
                .build();

        Reaction reaction = Reaction.builder()
                .id(2L)
                .component(componentName)
                .system("TestSystem")
                .build();

        Trigger trigger = Trigger.builder()
                .source(message)
                .target(reaction)
                .median(5)
                .build();

        Graph componentGraph = new Graph(List.of(message, reaction), List.of(trigger));

        GraphSnapshot snapshot = aSnapshotOf(componentGraph);
        when(graphHolder.getSnapshot()).thenReturn(snapshot);
        when(graphExtractor.getComponentRelatedGraph(componentGraph, componentName)).thenReturn(componentGraph);

        JeapAuthenticationToken authentication = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("reaction-observer-read")
                .build();

        mockMvc.perform(get("/api/graphs/components/{componentName}", componentName)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graph.nodes[0].id").value(1))
                .andExpect(jsonPath("$.graph.nodes[1].id").value(2))
                .andExpect(jsonPath("$.graph.edges[0].edgeType").value("TRIGGER"))
                .andExpect(jsonPath("$.fingerprint").value(snapshot.fingerprintOfComponent(componentName)));
    }

    @Test
    void testGetComponentRelatedGraph_notFound() throws Exception {
        String componentName = "UnknownComponent";

        Graph emptyGraph = new Graph(List.of(), List.of());

        when(graphHolder.getSnapshot()).thenReturn(aSnapshotOf(emptyGraph));
        when(graphExtractor.getComponentRelatedGraph(emptyGraph, componentName)).thenReturn(emptyGraph);

        JeapAuthenticationToken authentication = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("reaction-observer-read")
                .build();

        mockMvc.perform(get("/api/graphs/components/{componentName}", componentName)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(authentication)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetMessageTypeRelatedGraphs_withAndWithoutVariant() throws Exception {
        String messageType = "TestType";

        // Message with variant
        Message messageWithVariant = Message.builder()
                .id(1L)
                .messageType(messageType)
                .variant("v1")
                .semantic(SemanticType.EVENT)
                .build();

        // Message without variant (null)
        Message messageWithoutVariant = Message.builder()
                .id(2L)
                .messageType(messageType)
                .variant(null)
                .semantic(SemanticType.EVENT)
                .build();

        Reaction reaction = Reaction.builder()
                .id(3L)
                .component("ComponentX")
                .system("SystemX")
                .build();

        Trigger trigger1 = Trigger.builder()
                .source(messageWithVariant)
                .target(reaction)
                .median(5)
                .build();

        Trigger trigger2 = Trigger.builder()
                .source(messageWithoutVariant)
                .target(reaction)
                .median(3)
                .build();

        Graph fullGraph = new Graph(
                List.of(messageWithVariant, messageWithoutVariant, reaction),
                List.of(trigger1, trigger2)
        );

        when(graphHolder.getSnapshot()).thenReturn(aSnapshotOf(fullGraph));

        // Mock subgraphs for both variants
        Graph subgraphWithVariant = new Graph(List.of(messageWithVariant, reaction), List.of(trigger1));
        Graph subgraphWithoutVariant = new Graph(List.of(messageWithoutVariant, reaction), List.of(trigger2));

        when(graphExtractor.getMessageRelatedGraph(fullGraph, messageType, "v1")).thenReturn(subgraphWithVariant);
        when(graphExtractor.getMessageRelatedGraph(fullGraph, messageType, null)).thenReturn(subgraphWithoutVariant);

        GraphDto dtoWithVariant = GraphDtoMapper.map(subgraphWithVariant);
        GraphDto dtoWithoutVariant = GraphDtoMapper.map(subgraphWithoutVariant);

        when(fingerprintCalculator.calculate(dtoWithVariant)).thenReturn("fp-v1");
        when(fingerprintCalculator.calculate(dtoWithoutVariant)).thenReturn("fp-null");

        JeapAuthenticationToken authentication = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("reaction-observer-read")
                .build();

        mockMvc.perform(get("/api/graphs/messages/{messageType}", messageType)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['TestType/v1'].fingerprint").value("fp-v1"))
                .andExpect(jsonPath("$.['TestType'].fingerprint").value("fp-null"));
    }

    // --- Conditional requests ------------------------------------------------------------------------------

    /**
     * A graph carries the fingerprint as its entity tag, and a caller that already has it is told so.
     * <p>
     * <b>Answered from the snapshot</b>: the extractor is never asked, which is what makes a round of
     * conditional requests cheap for the observer as well as for its consumer.
     */
    @Test
    void aSystemGraph_askedWithItsEntityTag_isNotModifiedAndExtractsNothing() throws Exception {
        GraphSnapshot snapshot = aSnapshotOf(aSystemGraph());
        when(graphHolder.getSnapshot()).thenReturn(snapshot);

        mockMvc.perform(get("/api/graphs/systems/TestSystem")
                        .header(HttpHeaders.IF_NONE_MATCH,
                                "\"sha256:" + snapshot.fingerprintOfSystem("TestSystem") + "\"")
                        .with(authentication(reader())))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));

        verifyNoInteractions(graphExtractor);
    }

    /**
     * And the tag it is answered with is the snapshot's - the same string the index publishes, not a value
     * recomputed from the body.
     */
    @Test
    void aSystemGraph_askedWithAStaleEntityTag_isAnsweredWithItsCurrentTag() throws Exception {
        Graph graph = aSystemGraph();
        GraphSnapshot snapshot = aSnapshotOf(graph);
        when(graphHolder.getSnapshot()).thenReturn(snapshot);
        when(graphExtractor.getSystemRelatedGraph(graph, "TestSystem")).thenReturn(graph);

        mockMvc.perform(get("/api/graphs/systems/TestSystem")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"sha256:what-it-had-before\"")
                        .with(authentication(reader())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG,
                        "\"sha256:" + snapshot.fingerprintOfSystem("TestSystem") + "\""))
                .andExpect(jsonPath("$.fingerprint").value(snapshot.fingerprintOfSystem("TestSystem")));
    }

    /** A name the graph does not have is a 404, tag or no tag - a consumer must be able to tell the two apart. */
    @Test
    void aSystemGraph_ofAnUnknownSystem_isNotFound() throws Exception {
        Graph empty = new Graph(List.of(), List.of());
        when(graphHolder.getGraph()).thenReturn(empty);
        when(graphHolder.getSnapshot()).thenReturn(GraphSnapshot.empty());
        when(graphExtractor.getSystemRelatedGraph(empty, "no-such-system")).thenReturn(empty);

        mockMvc.perform(get("/api/graphs/systems/no-such-system").with(authentication(reader())))
                .andExpect(status().isNotFound());
    }

    private static JeapAuthenticationToken reader() {
        return JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("reaction-observer-read")
                .build();
    }

    /** One system, one component, one message - enough for every subgraph to exist. */
    private static Graph aSystemGraph() {
        Message message = Message.builder().id(1L).messageType("TestType").semantic(SemanticType.EVENT).build();
        Reaction reaction = Reaction.builder().id(2L).component("TestComponent").system("TestSystem").build();
        return new Graph(List.of(message, reaction),
                List.of(Trigger.builder().source(message).target(reaction).build()));
    }

    /**
     * The real snapshot of a graph, built the way a refresh builds it - so the tags under test are the ones
     * production computes rather than strings chosen here.
     */
    private static GraphSnapshot aSnapshotOf(Graph graph) {
        return new GraphSnapshotFactory(new GraphExtractor(),
                new GraphFingerprintCalculator(JsonMapper.builder().build()),
                new EtagSupport(JsonMapper.builder().build()), "").of(graph);
    }
}
