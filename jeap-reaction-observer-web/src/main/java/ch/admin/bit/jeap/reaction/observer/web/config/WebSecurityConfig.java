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
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Slf4j
@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {

    @Value("${jeap.reaction.observer.read-user.username}")
    private String readUserUsername;

    @Value("${jeap.reaction.observer.read-user.password}")
    private String readUserPassword;

    @Value("${jeap.reaction.observer.write-user.username}")
    private String writeUserUsername;

    @Value("${jeap.reaction.observer.write-user.password}")
    private String writeUserPassword;

    /**
     * The API's own chain, which authenticates <b>either</b> way: HTTP Basic with the two in-memory users, and
     * - where the instance configures a resource server - a bearer token.
     * <p>
     * <b>Both on one chain, rather than letting bearer requests fall through to the starter's.</b> That chain
     * enables CSRF with a cookie repository, which is right for a browser-facing API and wrong for this one: a
     * client that posts with a bearer token would need a CSRF cookie it has no way of having. This API is
     * stateless and token- or password-authenticated, so CSRF protection buys nothing here and is disabled -
     * on both mechanisms, which is only possible if both are handled here.
     * <p>
     * Authorization is on the handler methods, through {@link ReactionsApiAuthorization}, which accepts either
     * an in-memory role or the semantic role of a token. {@code ReactionsApiRoleCoverageTest} fails the build
     * if a handler under {@code /api} appears without it.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 11)
        // same as on the deprecated WebSecurityConfigurerAdapter
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                               ObjectProvider<JeapJwtDecoderFactory> jwtDecoderFactory,
                                               ObjectProvider<AuthoritiesResolver> authoritiesResolver)
            throws Exception {
        http
                .securityMatcher("/api/**", "/error")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(requests -> requests
                        // Every GET is let through here and authorized on the handler method, so that one
                        // rule decides for both authentication mechanisms - see ReactionsApiAuthorization.
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Anything else has to be authenticated; what it may then do is the method's business.
                        .anyRequest().authenticated());

        http.authenticationManager(createApiAuthManager(http.getSharedObject(AuthenticationManagerBuilder.class)));

        configureBearerTokens(http, jwtDecoderFactory, authoritiesResolver);

        return http.build();
    }

    /**
     * Accepts bearer tokens as well - <b>only</b> when this instance is configured as a resource server.
     * <p>
     * {@link JeapJwtDecoderFactory} is a bean of the jEAP security starter exactly when an issuer is
     * configured. Without one there is no decoder to validate a token with, so a request carrying a bearer
     * token is authenticated by basic auth like any other - which is to say refused. <b>Configuring the
     * resource server unconditionally, or excluding bearer requests from this chain so they fall elsewhere,
     * would turn a bearer string into a way past the basic-auth users on an instance that has no OAuth at
     * all.</b>
     */
    private void configureBearerTokens(HttpSecurity http,
                                       ObjectProvider<JeapJwtDecoderFactory> jwtDecoderFactory,
                                       ObjectProvider<AuthoritiesResolver> authoritiesResolver) throws Exception {
        JeapJwtDecoderFactory decoderFactory = jwtDecoderFactory.getIfAvailable();
        if (decoderFactory == null) {
            log.info("No OAuth2 resource server is configured, the API accepts HTTP Basic only. Configure " +
                     "'jeap.security.oauth2.resourceserver.authorization-server.issuer' and " +
                     "'jeap.security.oauth2.resourceserver.system-name' to accept bearer tokens authorized " +
                     "with the semantic role '<system-name>_@{}_#{}'.",
                    ReactionsApiAuthorization.RESOURCE, ReactionsApiAuthorization.READ_OPERATION);
            return;
        }
        JwtDecoder jwtDecoder = decoderFactory.createJwtDecoder();
        JeapAuthenticationConverter authenticationConverter = authoritiesResolver.getIfAvailable() == null
                ? new JeapAuthenticationConverter()
                : new JeapAuthenticationConverter(authoritiesResolver.getObject());
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder)
                .jwtAuthenticationConverter(authenticationConverter)));
        log.info("The API accepts HTTP Basic and bearer tokens authorized with the semantic role " +
                 "'<system-name>_@{}_#{}' or '<system-name>_@{}_#{}'.",
                ReactionsApiAuthorization.RESOURCE, ReactionsApiAuthorization.READ_OPERATION,
                ReactionsApiAuthorization.RESOURCE, ReactionsApiAuthorization.WRITE_OPERATION);
    }

    private AuthenticationManager createApiAuthManager(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
                .withUser(readUserUsername).password(readUserPassword).roles(ReactionsApiAuthorization.READ_ROLE).and()
                .withUser(writeUserUsername).password(writeUserPassword).roles(ReactionsApiAuthorization.WRITE_ROLE);
        return auth.build();
    }

}
