package ch.admin.bit.jeap.reaction.observer.web.config;

import ch.admin.bit.jeap.security.resource.validation.JeapJwtDecoderFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

class WebSecurityConfigTest {

    /**
     * An instance that configures an authorization server without a system name would accept tokens and
     * authorize none of them, because the semantic role is only evaluated when the starter has a system
     * name. That is a deployment error, so it stops the instance.
     */
    @Test
    void requireSystemName_withoutOne_failsTheStartupAndSaysWhatToConfigure() {
        assertThatIllegalStateException()
                .isThrownBy(() -> WebSecurityConfig.requireSystemName(""))
                .withMessageContaining(WebSecurityConfig.SYSTEM_NAME_PROPERTY)
                .withMessageContaining("_@reactions_#read");

        assertThatIllegalStateException().isThrownBy(() -> WebSecurityConfig.requireSystemName(null));
        assertThatIllegalStateException().isThrownBy(() -> WebSecurityConfig.requireSystemName("   "));
    }

    @Test
    void requireSystemName_withOne_isSatisfied() {
        assertThatCode(() -> WebSecurityConfig.requireSystemName("myplatform")).doesNotThrowAnyException();
    }

    /**
     * And there is no fallback to HTTP Basic alone: an instance without an authorization server does not
     * start, rather than serving an API a token-authenticating consumer cannot use.
     */
    @Test
    void requireResourceServer_withoutOne_failsTheStartupAndSaysWhatToConfigure() {
        assertThatIllegalStateException()
                .isThrownBy(() -> WebSecurityConfig.requireResourceServer(null))
                .withMessageContaining(WebSecurityConfig.ISSUER_PROPERTY)
                .withMessageContaining(WebSecurityConfig.SYSTEM_NAME_PROPERTY);
    }

    @Test
    void requireResourceServer_withOne_answersIt() {
        JeapJwtDecoderFactory factory = mock(JeapJwtDecoderFactory.class);

        assertThat(WebSecurityConfig.requireResourceServer(factory)).isSameAs(factory);
    }
}
