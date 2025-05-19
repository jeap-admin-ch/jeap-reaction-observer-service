
package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatistics;
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

import java.util.Collections;
import java.util.List;

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
    void testGetStatisticsForComponent() throws Exception {
        // Arrange
        String component = "testComponent";
        List<ObservedReactionsAggregatedStatistics> mockStatistics = Collections.singletonList(
                new ObservedReactionsAggregatedStatistics(component, "triggerType", "triggerFqn", null, null, 5, 10, 15d)
        );

        when(observedReactionsAggregatedRepository.getStatistics(any(), any())).thenReturn(mockStatistics);

        // Act & Assert
        mockMvc.perform(get("/api/statistics/{component}", component)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(httpBasic("read", "secret"))
                )
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"component\":\"testComponent\",\"triggerType\":\"triggerType\",\"triggerFqn\":\"triggerFqn\",\"actionType\":null,\"actionFqn\":null,\"count\":5,\"median\":10.0,\"percentage\":15.0}]"));
    }
}