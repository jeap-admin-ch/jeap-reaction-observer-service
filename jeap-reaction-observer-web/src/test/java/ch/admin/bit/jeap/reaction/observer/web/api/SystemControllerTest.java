package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.SystemRepository;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.web.config.WebSecurityConfig;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import ch.admin.bit.jeap.security.test.resource.JeapAuthenticationTestTokenBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import ch.admin.bit.jeap.security.resource.configuration.MvcSecurityConfiguration;
import ch.admin.bit.jeap.security.resource.properties.ResourceServerProperties;
import ch.admin.bit.jeap.security.resource.token.TokenConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@AutoConfigureMockMvc
@Import({WebSecurityConfig.class, ReactionObserverProperties.class})
// The resource server is required as of 11.0.0, so a slice needs the starter's security configuration - the
// same beans production has - together with the properties it reads and the issuer and system name that
// src/test/resources/application.yml sets
@ImportAutoConfiguration({ResourceServerProperties.class, TokenConfiguration.class,
        MvcSecurityConfiguration.class})
@EnableWebSecurity
class SystemControllerTest {

    private static final String READ_ROLE = "reactionobserver_@reactions_#read";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemRepository systemRepository;

    @Test
    void testGetSystemNames() throws Exception {
        List<String> systems = List.of("TestSystem1", "TestSystem2");
        when(systemRepository.getSystemNames()).thenReturn(systems);

        // Act & Assert
                mockMvc.perform(get("/api/systems/names")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(readRoleAuthentication()))
                )
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0]").value("TestSystem1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1]").value("TestSystem2"));
    }

    @Test
    void testGetSystemNames_withNullValue() throws Exception {
        List<String> systems = new ArrayList<>();
        systems.add("TestSystem1");
        systems.add(null);
        systems.add("TestSystem2");
        when(systemRepository.getSystemNames()).thenReturn(systems);

        // Act & Assert
                mockMvc.perform(get("/api/systems/names")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(readRoleAuthentication()))
                )
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0]").value("TestSystem1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1]").value("TestSystem2"));
    }

    private static JeapAuthenticationToken readRoleAuthentication() {
        return JeapAuthenticationTestTokenBuilder.create().withUserRoles(READ_ROLE).build();
    }
}
