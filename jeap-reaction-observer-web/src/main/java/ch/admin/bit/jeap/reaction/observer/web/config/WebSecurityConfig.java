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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.util.StringUtils;

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

    /** What activates semantic authorization in the jEAP security starter, and the first part of the role. */
    static final String SYSTEM_NAME_PROPERTY = "jeap.security.oauth2.resourceserver.system-name";

    /** What makes the jEAP security starter a resource server at all, and so is required as well. */
    static final String ISSUER_PROPERTY = "jeap.security.oauth2.resourceserver.authorization-server.issuer";

    @Value("${" + SYSTEM_NAME_PROPERTY + ":}")
    private String systemName;

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

        // The in-memory users as an ordinary provider of this chain, rather than an AuthenticationManager
        // built here: a manager set on the chain is the one basic auth uses, and the JWT provider that
        // oauth2ResourceServer adds below has to end up in the same manager. Registering both as providers
        // is what makes that true by construction instead of by the order they happen to be added in.
        http.authenticationProvider(inMemoryUsers());

        configureBearerTokens(http, jwtDecoderFactory, authoritiesResolver);

        return http.build();
    }

    /**
     * Accepts bearer tokens, which is <b>not optional</b>: an instance that configures no resource server
     * does not start.
     * <p>
     * There used to be a fallback here - no issuer configured meant the API served HTTP Basic alone. It is
     * gone on purpose. A consumer that replicates from this service authenticates with a token, and an
     * instance that quietly serves only passwords looks healthy while being unusable to it; the failure then
     * surfaces as a {@code 401} in somebody else's import hours later, rather than as a refusal to start
     * here. <b>Two mechanisms are what this API supports, so both are required to be configured.</b>
     * <p>
     * HTTP Basic keeps working exactly as before - what is required is that OAuth2 works <em>too</em>.
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
        log.info("The API accepts HTTP Basic and bearer tokens authorized with the semantic role " +
                 "'{}_@{}_#{}' or '{}_@{}_#{}'.",
                systemName, ReactionsApiAuthorization.RESOURCE, ReactionsApiAuthorization.READ_OPERATION,
                systemName, ReactionsApiAuthorization.RESOURCE, ReactionsApiAuthorization.WRITE_OPERATION);
    }

    /** What springdoc publishes, and what this service has never served to anyone. */
    private static final String[] API_DOCUMENTATION_PATHS =
            {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};

    /**
     * The OpenAPI document and the Swagger UI stay denied, as they were before the resource server became
     * mandatory.
     * <p>
     * The jEAP security starter has two fallback chains at the very back and exactly one of them is active:
     * {@code DefaultDenyAllWebSecurityConfiguration} ({@code denyAll}), while no resource server is
     * configured, and {@code MvcSecurityConfiguration}'s ({@code anyRequest().fullyAuthenticated()}) once one
     * is - the first is {@code @ConditionalOnMissingBean} of the second. Requiring an issuer therefore swaps
     * one for the other, and these paths would go from unreachable to readable by anyone holding any token
     * that issuer signed, whatever roles it carries.
     * <p>
     * <b>That is a change nobody asked for</b>, so it is undone here, for exactly the paths it would have
     * affected. The actuator has its own chain from the jEAP monitoring starter, far ahead of this one and
     * unaffected. An instance that wants to publish its OpenAPI document overrides this with a chain of its
     * own - deliberately, which is the point.
     * <p>
     * It carries a matcher rather than {@code anyRequest()}: two any-request chains in one application are
     * rejected as unreachable, and the starter's is the one that has to stay for {@code /error} and anything
     * else neither chain names.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 1)
    SecurityFilterChain apiDocumentationDeniedFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(API_DOCUMENTATION_PATHS)
                .authorizeHttpRequests(requests -> requests.anyRequest().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
                .build();
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
                    + SYSTEM_NAME_PROPERTY + "'). This API is authenticated with HTTP Basic and with bearer "
                    + "tokens, and both are required: an instance that served passwords alone would look "
                    + "healthy while being unusable to a consumer that authenticates with a token.");
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
                    "semantic role '<system-name>_@" + ReactionsApiAuthorization.RESOURCE + "_#" +
                    ReactionsApiAuthorization.READ_OPERATION + "', which the jEAP security starter only " +
                    "evaluates when the system name is configured. Configure it.");
        }
    }

    /**
     * The two configured users, with the roles they have always had. The passwords carry their encoding as a
     * prefix ({@code {noop}...}), which the delegating encoder of {@link DaoAuthenticationProvider} reads.
     */
    private AuthenticationProvider inMemoryUsers() {
        UserDetails readUser = User.withUsername(readUserUsername)
                .password(readUserPassword)
                .roles(ReactionsApiAuthorization.READ_ROLE)
                .build();
        UserDetails writeUser = User.withUsername(writeUserUsername)
                .password(writeUserPassword)
                .roles(ReactionsApiAuthorization.WRITE_ROLE)
                .build();
        return new DaoAuthenticationProvider(new InMemoryUserDetailsManager(readUser, writeUser));
    }

}
