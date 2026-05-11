package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GraphController.class)
@AutoConfigureMockMvc
@Import({WebSecurityConfig.class, ReactionObserverProperties.class})
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

        when(graphHolder.getGraph()).thenReturn(domainGraph);

        GraphDto graphDto = new GraphDto(
                List.of(
                        new MessageNodeDto(1L, "TestType", "v1"),
                        new ReactionNodeDto(2L, "TestComponent")
                ),
                List.of(
                        new TriggerEdgeDto(1L, NodeDtoType.MESSAGE, 2L, 5)
                )
        );

        String expectedFingerprint = "abc123fingerprint";
        when(fingerprintCalculator.calculate(graphDto)).thenReturn(expectedFingerprint);

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

        when(graphHolder.getGraph()).thenReturn(fullGraph);
        when(graphExtractor.getSystemRelatedGraph(fullGraph, systemName)).thenReturn(fullGraph);
        when(fingerprintCalculator.calculate(GraphDtoMapper.map(fullGraph))).thenReturn("abc123fingerprint");

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
                .andExpect(jsonPath("$.fingerprint").value("abc123fingerprint"));
    }

    @Test
    void testGetSystemRelatedGraph_notFound() throws Exception {
        String systemName = "UnknownSystem";

        Graph fullGraph = new Graph(List.of(), List.of());

        when(graphHolder.getGraph()).thenReturn(fullGraph);
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

        when(graphHolder.getGraph()).thenReturn(componentGraph);
        when(graphExtractor.getComponentRelatedGraph(componentGraph, componentName)).thenReturn(componentGraph);
        when(fingerprintCalculator.calculate(GraphDtoMapper.map(componentGraph))).thenReturn("component-fingerprint");

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
                .andExpect(jsonPath("$.fingerprint").value("component-fingerprint"));
    }

    @Test
    void testGetComponentRelatedGraph_notFound() throws Exception {
        String componentName = "UnknownComponent";

        Graph emptyGraph = new Graph(List.of(), List.of());

        when(graphHolder.getGraph()).thenReturn(emptyGraph);
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

        when(graphHolder.getGraph()).thenReturn(fullGraph);

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
}
