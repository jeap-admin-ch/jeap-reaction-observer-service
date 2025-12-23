package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.ComponentRepository;
import ch.admin.bit.jeap.reaction.observer.web.config.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.web.config.WebSecurityConfig;
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ComponentController.class)
@AutoConfigureMockMvc
@Import({WebSecurityConfig.class, ReactionObserverProperties.class})
class ComponentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComponentRepository componentRepository;

    @Test
    void getComponentNames() throws Exception {
        List<String> components = List.of("TestComponent1", "TestComponent2");
        when(componentRepository.getComponentNames()).thenReturn(components);

        JeapAuthenticationToken authentication = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("reaction-observer-read")
                .build();
        mockMvc.perform(get("/api/components/names")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0]").value("TestComponent1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1]").value("TestComponent2"));
    }

    @Test
    void getComponentNames_accessDenied() throws Exception {
        JeapAuthenticationToken authentication = JeapAuthenticationTestTokenBuilder.create()
                .withUserRoles("foo-role")
                .build();
        mockMvc.perform(get("/api/components/names")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(authentication(authentication)))
                .andExpect(status().isForbidden());
    }
}
