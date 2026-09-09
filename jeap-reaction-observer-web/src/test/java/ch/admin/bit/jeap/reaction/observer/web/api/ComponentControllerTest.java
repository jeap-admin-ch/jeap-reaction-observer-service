package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.ComponentRepository;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionsApiAuthorization;
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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ComponentController.class)
@AutoConfigureMockMvc
@Import({WebSecurityConfig.class, ReactionObserverProperties.class, ReactionsApiAuthorization.class})
// The resource server is required as of 11.0.0, so a slice needs the starter's security configuration - the
// same beans production has - together with the properties it reads and the issuer and system name that
// src/test/resources/application.yml sets
@ImportAutoConfiguration({ResourceServerProperties.class, TokenConfiguration.class,
        MvcSecurityConfiguration.class})
@EnableWebSecurity
class ComponentControllerTest {

    /** The read user of the test configuration - the basic-auth half of the API's two mechanisms. */
    private static final String READ_USER = "read";
    private static final String READ_PASSWORD = "secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComponentRepository componentRepository;

    @Test
    void getComponentNames() throws Exception {
        List<String> components = List.of("TestComponent1", "TestComponent2");
        when(componentRepository.getComponentNames()).thenReturn(components);

                mockMvc.perform(get("/api/components/names")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(httpBasic(READ_USER, READ_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0]").value("TestComponent1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1]").value("TestComponent2"));
    }

    /** A token carrying some other role is refused - as is any token without the semantic read role. */
    @Test
    void getComponentNames_accessDenied() throws Exception {
        JeapAuthenticationToken withAnotherRole = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("foo-role")
                .build();
        mockMvc.perform(get("/api/components/names")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(withAnotherRole)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getComponentNames_withTheWrongPassword_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/components/names")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(httpBasic(READ_USER, "not-the-password")))
                .andExpect(status().isUnauthorized());
    }
}
