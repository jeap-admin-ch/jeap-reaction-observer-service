package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.web.config.WebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({WebSecurityConfig.class, ReactionObserverProperties.class})
@WebMvcTest(StatisticsController.class)
@AutoConfigureMockMvc
@EnableWebSecurity
class StatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;

    @Test
    void testGetLastObservedReactionDatePerComponent() throws Exception {
        // Arrange
        Map<String, LocalDate> mockStatistics = Map.of(
                "component1", LocalDate.of(2025,6,29),
                "component2", LocalDate.of(2022,5,22),
                "component3", LocalDate.of(1998,7,13),
                "component4", LocalDate.of(1997,1,1)
);

        when(observedReactionsAggregatedRepository.getLastObservedReactionDatePerComponent()).thenReturn(mockStatistics);

        // Act & Assert
        mockMvc.perform(get("/api/statistics/last-observation-date")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(httpBasic("read", "secret"))
                )
                .andExpect(status().isOk())
                .andExpect(content().json("{\"component1\":\"2025-06-29\",\"component2\":\"2022-05-22\",\"component3\":\"1998-07-13\",\"component4\":\"1997-01-01\"}"));
    }

}
