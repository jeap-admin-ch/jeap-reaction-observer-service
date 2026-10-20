package ch.admin.bit.jeap.reaction.observer.web.config;

import ch.admin.bit.jeap.security.resource.validation.JeapJwtDecoderFactory;
import ch.admin.bit.jeap.security.resource.token.AuthoritiesResolver;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationConverter;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {

    /** What activates semantic authorization in the jEAP security starter, and the first part of the role. */
    static final String SYSTEM_NAME_PROPERTY = "jeap.security.oauth2.resourceserver.system-name";

    /** What makes the jEAP security starter a resource server at all, and so is required as well. */
    static final String ISSUER_PROPERTY = "jeap.security.oauth2.resourceserver.authorization-server.issuer";

    private static final String RESOURCE = "reactions";
    private static final String READ_OPERATION = "read";
    private static final String WRITE_OPERATION = "write";

    @Value("${" + SYSTEM_NAME_PROPERTY + ":}")
    private String systemName;

    /**
     * The API's own chain, which authenticates bearer tokens.
     * <p>
     * <b>Bearer requests stay on this chain rather than falling through to the starter's.</b> That chain
     * enables CSRF with a cookie repository, which is right for a browser-facing API and wrong for this one:
     * a client that posts with a bearer token would need a CSRF cookie it has no way of having. This API is
     * stateless and token-authenticated, so CSRF protection buys nothing here and is disabled.
     * <p>
     * Authorization is on the handler methods with the jEAP semantic roles.
     * {@code ReactionsApiRoleCoverageTest} fails the build if a handler under {@code /api} appears without it.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 11)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                               ObjectProvider<JeapJwtDecoderFactory> jwtDecoderFactory,
                                               ObjectProvider<AuthoritiesResolver> authoritiesResolver)
            throws Exception {
        http
                .securityMatcher("/api/**", "/error")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Method security checks each authenticated caller's semantic role.
                        .anyRequest().authenticated());

        configureBearerTokens(http, jwtDecoderFactory, authoritiesResolver);

        return http.build();
    }

    /**
     * Accepts bearer tokens, which is <b>not optional</b>: an instance that configures no resource server
     * does not start.
     */
    private void configureBearerTokens(HttpSecurity http,
                                       ObjectProvider<JeapJwtDecoderFactory> jwtDecoderFactory,
                                       ObjectProvider<AuthoritiesResolver> authoritiesResolver) throws Exception {
        JeapJwtDecoderFactory decoderFactory = requireResourceServer(jwtDecoderFactory.getIfAvailable());
        requireSystemName(systemName);
        JwtDecoder jwtDecoder = decoderFactory.createJwtDecoder();
        JeapAuthenticationConverter authenticationConverter = authoritiesResolver.getIfAvailable() == null
                ? new JeapAuthenticationConverter()
                : new JeapAuthenticationConverter(authoritiesResolver.getObject());
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder)
                .jwtAuthenticationConverter(authenticationConverter)));
        log.info("The API accepts bearer tokens authorized with the semantic role '{}_@{}_#{}' or '{}_@{}_#{}'.",
                systemName, RESOURCE, READ_OPERATION, systemName, RESOURCE, WRITE_OPERATION);
    }

    /**
     * The resource server itself, which every instance has to configure.
     * <p>
     * {@link JeapJwtDecoderFactory} is a bean of the jEAP security starter exactly when an issuer is
     * configured, so its absence is the absence of an authorization server - and there would then be nothing
     * to validate a token with.
     */
    static JeapJwtDecoderFactory requireResourceServer(JeapJwtDecoderFactory decoderFactory) {
        if (decoderFactory == null) {
            throw new IllegalStateException(
                    "No authorization server is configured: set '" + ISSUER_PROPERTY + "' (and '"
                    + SYSTEM_NAME_PROPERTY + "'). This API requires bearer tokens to authenticate requests.");
        }
        return decoderFactory;
    }

    /**
     * A resource server without a system name would accept tokens and authorize none of them: the semantic
     * role this API is authorized with only exists when the jEAP security starter has a system name, and
     * without one the starter installs the simple role model instead - under which
     * {@code <system-name>_@reactions_#read} is an opaque string nothing checks.
     * <p>
     * <b>So the instance is stopped here rather than serving an API that refuses every token.</b> A
     * configuration error belongs in the deployment, not in the first request.
     */
    static void requireSystemName(String systemName) {
        if (!StringUtils.hasText(systemName)) {
            throw new IllegalStateException(
                    "'" + SYSTEM_NAME_PROPERTY + "' is not set. The API authorizes a bearer token with the " +
                    "semantic role '<system-name>_@" + RESOURCE + "_#" + READ_OPERATION +
                    "', which the jEAP security starter only " +
                    "evaluates when the system name is configured. Configure it.");
        }
    }

}
