
package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.Action;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatisticsV2;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.web.config.WebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({WebSecurityConfig.class, ReactionObserverProperties.class})
@WebMvcTest(StatisticsController.class)
@AutoConfigureMockMvc
class StatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;

    @Test
    void testGetStatisticsForComponentNoActions_backwards_compatibility() throws Exception {
        // Arrange
        String component = "testComponent";
        List<ObservedReactionsAggregatedStatisticsV2> mockStatistics = Collections.singletonList(
                new ObservedReactionsAggregatedStatisticsV2(component, "triggerType", "triggerFqn", new ArrayList<>(), 5, 10, 15d, Collections.singletonMap("triggerProp", "triggerVal"))
        );

        when(observedReactionsAggregatedRepository.getStatistics(any(), any())).thenReturn(mockStatistics);

        // Act & Assert
        mockMvc.perform(get("/api/statistics/{component}", component)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(httpBasic("read", "secret"))
                )
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"component\":\"testComponent\",\"triggerType\":\"triggerType\",\"triggerFqn\":\"triggerFqn\",\"actionType\":null,\"actionFqn\":null,\"count\":5,\"median\":10.0,\"percentage\":15.0,\"triggerProperties\":{\"triggerProp\":\"triggerVal\"},\"actionProperties\":{}}]"));
    }

    @Test
    void testGetStatisticsForComponentSingleAction_backwards_compatibility() throws Exception {
        // Arrange
        String component = "testComponent";
        Action action = new Action("actionType", "actionFqn", Collections.singletonMap("actionProp", "actionVal"));
        List<ObservedReactionsAggregatedStatisticsV2> mockStatistics = Collections.singletonList(
                new ObservedReactionsAggregatedStatisticsV2(component, "triggerType", "triggerFqn", singletonList(action), 5, 10, 15d, Collections.singletonMap("triggerProp", "triggerVal"))
        );

        when(observedReactionsAggregatedRepository.getStatistics(any(), any())).thenReturn(mockStatistics);

        // Act & Assert
        mockMvc.perform(get("/api/statistics/{component}", component)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(httpBasic("read", "secret"))
                )
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"component\":\"testComponent\",\"triggerType\":\"triggerType\",\"triggerFqn\":\"triggerFqn\",\"actionType\":\"actionType\",\"actionFqn\":\"actionFqn\",\"count\":5,\"median\":10.0,\"percentage\":15.0,\"triggerProperties\":{\"triggerProp\":\"triggerVal\"},\"actionProperties\":{\"actionProp\":\"actionVal\"}}]"));
    }

    @Test
    void testGetStatisticsForComponentMultipleActions_onlyFirstIsUsed_backwards_compatibility() throws Exception {
        // Arrange
        String component = "testComponent";
        Action action = new Action("actionType", "actionFqn", Collections.singletonMap("actionProp", "actionVal"));
        Action action1 = new Action("actionType1", "actionFqn1", Collections.singletonMap("actionProp1", "actionVal1"));
        List<ObservedReactionsAggregatedStatisticsV2> mockStatistics = Collections.singletonList(
                new ObservedReactionsAggregatedStatisticsV2(component, "triggerType", "triggerFqn", List.of(action, action1), 5, 10, 15d, Collections.singletonMap("triggerProp", "triggerVal"))
        );

        when(observedReactionsAggregatedRepository.getStatistics(any(), any())).thenReturn(mockStatistics);

        // Act & Assert
        mockMvc.perform(get("/api/statistics/{component}", component)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(httpBasic("read", "secret"))
                )
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"component\":\"testComponent\",\"triggerType\":\"triggerType\",\"triggerFqn\":\"triggerFqn\",\"actionType\":\"actionType\",\"actionFqn\":\"actionFqn\",\"count\":5,\"median\":10.0,\"percentage\":15.0,\"triggerProperties\":{\"triggerProp\":\"triggerVal\"},\"actionProperties\":{\"actionProp\":\"actionVal\"}}]"));
    }

    @Test
    void testGetStatisticsForComponentNoActions() throws Exception {
        // Arrange
        String component = "testComponent";
        List<ObservedReactionsAggregatedStatisticsV2> mockStatistics = Collections.singletonList(
                new ObservedReactionsAggregatedStatisticsV2(component, "triggerType", "triggerFqn", new ArrayList<>(), 5, 10, 15d, Collections.singletonMap("triggerProp", "triggerVal"))
        );

        when(observedReactionsAggregatedRepository.getStatistics(any(), any())).thenReturn(mockStatistics);

        // Act & Assert
        mockMvc.perform(get("/api/statisticsV2/{component}", component)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(httpBasic("read", "secret"))
                )
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"component\":\"testComponent\",\"triggerType\":\"triggerType\",\"triggerFqn\":\"triggerFqn\",\"actions\":[],\"count\":5,\"median\":10.0,\"percentage\":15.0,\"triggerProperties\":{\"triggerProp\":\"triggerVal\"}}]"));
    }

    @Test
    void testGetStatisticsForComponentSingleAction() throws Exception {
        // Arrange
        String component = "testComponent";
        Action action = new Action("actionType", "actionFqn", Collections.singletonMap("actionProp", "actionVal"));
        List<ObservedReactionsAggregatedStatisticsV2> mockStatistics = Collections.singletonList(
                new ObservedReactionsAggregatedStatisticsV2(component, "triggerType", "triggerFqn", singletonList(action), 5, 10, 15d, Collections.singletonMap("triggerProp", "triggerVal"))
        );

        when(observedReactionsAggregatedRepository.getStatistics(any(), any())).thenReturn(mockStatistics);

        // Act & Assert
        mockMvc.perform(get("/api/statisticsV2/{component}", component)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(httpBasic("read", "secret"))
                )
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"component\":\"testComponent\",\"triggerType\":\"triggerType\",\"triggerFqn\":\"triggerFqn\",\"actions\":[{\"actionType\":\"actionType\",\"actionFqn\":\"actionFqn\",\"actionProperties\":{\"actionProp\":\"actionVal\"}}],\"count\":5,\"median\":10.0,\"percentage\":15.0,\"triggerProperties\":{\"triggerProp\":\"triggerVal\"}}]"));
    }

    @Test
    void testGetStatisticsForComponentMultipleActions() throws Exception {
        // Arrange
        String component = "testComponent";
        Action action = new Action("actionType", "actionFqn", Collections.singletonMap("actionProp", "actionVal"));
        Action action1 = new Action("actionType1", "actionFqn1", Collections.singletonMap("actionProp1", "actionVal1"));
        List<ObservedReactionsAggregatedStatisticsV2> mockStatistics = Collections.singletonList(
                new ObservedReactionsAggregatedStatisticsV2(component, "triggerType", "triggerFqn", List.of(action, action1), 5, 10, 15d, Collections.singletonMap("triggerProp", "triggerVal"))
        );

        when(observedReactionsAggregatedRepository.getStatistics(any(), any())).thenReturn(mockStatistics);

        // Act & Assert
        mockMvc.perform(get("/api/statisticsV2/{component}", component)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(httpBasic("read", "secret"))
                )
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"component\":\"testComponent\",\"triggerType\":\"triggerType\",\"triggerFqn\":\"triggerFqn\",\"actions\":[{\"actionType\":\"actionType\",\"actionFqn\":\"actionFqn\",\"actionProperties\":{\"actionProp\":\"actionVal\"}},{\"actionType\":\"actionType1\",\"actionFqn\":\"actionFqn1\",\"actionProperties\":{\"actionProp1\":\"actionVal1\"}}],\"count\":5,\"median\":10.0,\"percentage\":15.0,\"triggerProperties\":{\"triggerProp\":\"triggerVal\"}}]"));
    }
}
