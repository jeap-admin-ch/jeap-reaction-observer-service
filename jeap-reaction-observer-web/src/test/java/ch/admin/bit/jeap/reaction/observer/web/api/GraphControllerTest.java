package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.web.config.WebSecurityConfig;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.*;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import ch.admin.bit.jeap.security.test.resource.JeapAuthenticationTestTokenBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
class GraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GraphHolder graphHolder;

    @MockitoBean
    private GraphFingerprintCalculator fingerprintCalculator;

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
}
