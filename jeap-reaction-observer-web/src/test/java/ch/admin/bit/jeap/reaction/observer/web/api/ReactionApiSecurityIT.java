package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.SemanticType;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Trigger;
import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.ReactionObserverApplication;
import ch.admin.bit.jeap.reaction.observer.web.service.ScheduledTasksService;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationContext;
import ch.admin.bit.jeap.security.test.jws.JwsBuilder;
import ch.admin.bit.jeap.security.test.jws.JwsBuilderFactory;
import ch.admin.bit.jeap.security.test.resource.configuration.JeapOAuth2IntegrationTestResourceConfiguration;
import ch.admin.bit.jeap.security.test.resource.jwks.JwksEndpointMockBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That the API can be reached with <b>both</b> ways of authenticating, over HTTP, through a real servlet
 * container - and that neither of them runs into a CSRF token.
 * <p>
 * A real container rather than {@code MockMvc} on purpose: what is under test is the filter chain, the
 * {@code WWW-Authenticate} handling, the entity tags on the wire and the absence of CSRF enforcement. MockMvc
 * would exercise the same filters but not the container's own handling of a header or a status, and the
 * question here is precisely what a client sees.
 *
 * @see ch.admin.bit.jeap.reaction.observer.web.config.ReactionsApiAuthorization
 */
// The consumer contracts of this module are declared once, on IntegrationTestBase: the annotation processor
// writes one file per message type per module, so declaring them again here fails the compilation.
@SpringBootTest(classes = ReactionObserverApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JeapOAuth2IntegrationTestResourceConfiguration.class)
@Testcontainers
class ReactionApiSecurityIT extends KafkaIntegrationTestBase {

    /**
     * A real PostgreSQL: the migrations are Postgres SQL - {@code UPDATE ... SET ... FROM} in
     * {@code V1_5_0__add_interface_table.sql} alone rules H2 out - so the application context cannot start
     * against anything else.
     */
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    private static final String SYSTEM_NAME = "reactionobserver";
    private static final String CONTEXT_PATH = "/test";
    private static final String READ_ROLE = SYSTEM_NAME + "_@reactions_#read";
    private static final String WRITE_ROLE = SYSTEM_NAME + "_@reactions_#write";
    private static final String SOME_OTHER_ROLE = SYSTEM_NAME + "_@somethingelse_#read";

    /**
     * A port picked before the context starts, because the resource server has to be told where the JWKS is
     * and the JWKS is served by this very application. A random port would not be known in time.
     */
    private static final int PORT = freePort();

    private static final String BASE_URL = "http://localhost:" + PORT + CONTEXT_PATH;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("server.port", () -> PORT);
        registry.add("spring.application.name", () -> CONTEXT_PATH.substring(1));
        // Configuring an issuer is what makes this instance a resource server at all - see WebSecurityConfig
        registry.add("jeap.security.oauth2.resourceserver.authorization-server.issuer",
                () -> JwsBuilder.DEFAULT_ISSUER);
        registry.add("jeap.security.oauth2.resourceserver.authorization-server.jwk-set-uri",
                () -> BASE_URL + JwksEndpointMockBase.getJwksPath());
        // ... and configuring a system name is what activates semantic roles
        registry.add("jeap.security.oauth2.resourceserver.system-name", () -> SYSTEM_NAME);
        registry.add("jeap.reaction.observer.read-user.username", () -> "read");
        registry.add("jeap.reaction.observer.read-user.password", () -> "{noop}read-secret");
        registry.add("jeap.reaction.observer.write-user.username", () -> "write");
        registry.add("jeap.reaction.observer.write-user.password", () -> "{noop}write-secret");
    }

    /** The refresh must not run on its own schedule while this test drives the holder. */
    @MockitoBean
    private ScheduledTasksService scheduledTasksService;

    @Autowired
    private GraphHolder graphHolder;

    @Autowired
    private JwsBuilderFactory jwsBuilderFactory;

    private final RestClient restClient = RestClient.builder().baseUrl(BASE_URL).build();

    @BeforeEach
    void setUp() {
        graphHolder.setGraph(aGraph());
    }

    // --- HTTP Basic ----------------------------------------------------------------------------------------

    @Test
    void readEndpoint_withBasicAuthOfTheReadUser_isAnswered() {
        ResponseEntity<String> response = get("/api/graphs/systems", basic("read", "read-secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("orders");
        assertThat(response.getHeaders().getETag()).startsWith("\"sha256:");
    }

    @Test
    void readEndpoint_withBasicAuthOfTheWriteUser_isRefused() {
        // The two users are separate: writing does not imply reading, as it never has
        assertThat(get("/api/graphs/systems", basic("write", "write-secret")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void readEndpoint_withoutCredentials_isUnauthorized() {
        ResponseEntity<String> response = get("/api/graphs/systems", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("Basic");
    }

    @Test
    void readEndpoint_withTheWrongPassword_isUnauthorized() {
        assertThat(get("/api/graphs/systems", basic("read", "not-the-password")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Bearer tokens with the semantic role --------------------------------------------------------------

    @Test
    void readEndpoint_withATokenCarryingTheReadRole_isAnswered() {
        ResponseEntity<String> response = get("/api/graphs/systems", bearer(READ_ROLE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("orders");
    }

    @Test
    void everyReadResource_withATokenCarryingTheReadRole_isAnswered() {
        List<String> resources = List.of("/api/graphs", "/api/graphs/systems", "/api/graphs/components",
                "/api/graphs/messages", "/api/graphs/systems/orders", "/api/graphs/components/orders-intake",
                "/api/graphs/messages/OrdersPaymentAcceptedEvent", "/api/systems/names",
                "/api/components/names", "/api/statistics/last-observation-date");

        assertThat(resources).allSatisfy(resource ->
                assertThat(get(resource, bearer(READ_ROLE)).getStatusCode())
                        .describedAs(resource)
                        .isEqualTo(HttpStatus.OK));
    }

    @Test
    void readEndpoint_withATokenCarryingAnotherRole_isForbidden() {
        assertThat(get("/api/graphs/systems", bearer(SOME_OTHER_ROLE)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void readEndpoint_withATokenCarryingTheWriteRoleOnly_isForbidden() {
        assertThat(get("/api/graphs/systems", bearer(WRITE_ROLE)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void readEndpoint_withAnUnsignedToken_isUnauthorized() {
        assertThat(get("/api/graphs/systems", "Bearer not-a-token").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * The simple role a token may carry instead of the semantic one, which is what lets an authorization
     * server grant either spelling while consumers move.
     */
    @Test
    void readEndpoint_withATokenCarryingTheSimpleRole_isAnswered() {
        assertThat(get("/api/graphs/systems", bearer("reaction-observer-read")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // --- The write endpoint, and CSRF ----------------------------------------------------------------------

    @Test
    void writeEndpoint_withBasicAuthOfTheWriteUser_isAnswered() {
        assertThat(get("/api/management/aggregate-data/2026-09-09", basic("write", "write-secret"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void writeEndpoint_withATokenCarryingTheWriteRole_isAnswered() {
        assertThat(get("/api/management/aggregate-data/2026-09-09", bearer(WRITE_ROLE)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void writeEndpoint_withReadCredentialsOnly_isForbidden() {
        assertThat(get("/api/management/aggregate-data/2026-09-09", basic("read", "read-secret"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/management/aggregate-data/2026-09-09", bearer(READ_ROLE)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * <b>No CSRF token is needed, with either mechanism.</b>
     * <p>
     * A state-changing request that the filter chain rejected for a missing CSRF token would answer
     * {@code 403} <em>before</em> reaching the handler mapping. These posts get {@code 405 Method Not
     * Allowed} instead - the mapping's answer, since the aggregation is a {@code GET} - which is only
     * reachable once security has passed the request through. That is the assertion: not that a POST works,
     * but that CSRF is not what stops it.
     * <p>
     * It matters because the jEAP security starter's own filter chain enables CSRF with a cookie repository:
     * a bearer-token client that fell through to that chain would need a CSRF cookie it cannot have. This
     * API's chain handles both mechanisms itself and disables CSRF, and this test is what says so.
     */
    @Test
    void aPostIsNotRejectedByCsrf_withEitherMechanism() {
        assertThat(post("/api/management/aggregate-data/2026-09-09", basic("write", "write-secret"))
                .getStatusCode())
                .describedAs("basic auth: 405 from the handler mapping, not 403 from the CSRF filter")
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(post("/api/management/aggregate-data/2026-09-09", bearer(WRITE_ROLE)).getStatusCode())
                .describedAs("bearer token: 405 from the handler mapping, not 403 from the CSRF filter")
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void aPostWithoutCredentials_isUnauthorizedRatherThanForbidden() {
        // Not permitted, and not a CSRF failure either: unauthenticated is unauthenticated
        assertThat(post("/api/management/aggregate-data/2026-09-09", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Helpers -------------------------------------------------------------------------------------------

    private ResponseEntity<String> get(String path, String authorization) {
        return exchange(HttpMethod.GET, path, authorization);
    }

    private ResponseEntity<String> post(String path, String authorization) {
        return exchange(HttpMethod.POST, path, authorization);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        return restClient.method(method)
                .uri(path)
                .headers(h -> h.addAll(headers))
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .headers(response.getHeaders())
                        .body(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private String bearer(String... userRoles) {
        return "Bearer " + jwsBuilderFactory
                .createValidForFixedLongPeriodBuilder("test-client", JeapAuthenticationContext.SYS)
                .withUserRoles(userRoles)
                .build()
                .serialize();
    }

    /**
     * A graph with one system, one component and one message type, so that every read resource has something
     * to answer and the indexes are not empty.
     */
    private static Graph aGraph() {
        Message message = Message.builder()
                .id(1)
                .messageType("OrdersPaymentAcceptedEvent")
                .semantic(SemanticType.EVENT)
                .build();
        Reaction reaction = Reaction.builder()
                .id(2)
                .component("orders-intake")
                .system("orders")
                .build();
        return new Graph(List.of(message, reaction),
                List.of(Trigger.builder().source(message).target(reaction).median(3).build()));
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not find a free port for the test container", e);
        }
    }
}
