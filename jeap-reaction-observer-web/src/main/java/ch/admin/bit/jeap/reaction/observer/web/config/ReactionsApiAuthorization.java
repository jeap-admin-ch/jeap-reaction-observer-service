package ch.admin.bit.jeap.reaction.observer.web.config;

import ch.admin.bit.jeap.security.resource.semanticAuthentication.ServletSemanticAuthorization;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who may read the reaction graphs and who may trigger an aggregation - over <b>both</b> ways of
 * authenticating.
 * <p>
 * The API accepts HTTP Basic with the two in-memory users it has always had, and a bearer token authorized
 * with a semantic role:
 *
 * <table>
 *     <caption>The roles</caption>
 *     <tr><td>{@code <system-name>_@reactions_#read}</td><td>every {@code GET} under {@code /api}</td></tr>
 *     <tr><td>{@code <system-name>_@reactions_#write}</td><td>triggering an aggregation</td></tr>
 * </table>
 *
 * <b>No tenant part</b>, and that is deliberate: a tenant says which mandant may exercise a role, and there is
 * no such division here. A system's subgraph is cut out of one graph and carries the messages of other systems
 * by construction, and a consumer that documents a landscape reads every system of it.
 * <p>
 * <b>Why a bean and not {@code hasRole('reactions', 'read')} in the annotation:</b> the two-argument
 * expression exists only when semantic authorization is active, which the jEAP security starter ties to
 * {@code jeap.security.oauth2.resourceserver.system-name}. An instance that configures no resource server -
 * which is every instance until one is set up - would then fail every request with an unknown expression
 * instead of authorizing it by basic auth. So the choice is made here, in Java, where it can be conditional.
 * <p>
 * A token that carries the <em>simple</em> role {@code reaction-observer-read} is accepted as well, because
 * the starter maps a token's user roles to authorities: that is what lets an authorization server grant either
 * spelling while its consumers move.
 */
@Component("reactionsApiAuthorization")
@RequiredArgsConstructor
public class ReactionsApiAuthorization {

    /** The resource part of the semantic role - what this API is about. */
    public static final String RESOURCE = "reactions";

    public static final String READ_OPERATION = "read";
    public static final String WRITE_OPERATION = "write";

    /** The in-memory roles of the basic-auth users, and the simple roles a token may carry instead. */
    static final String READ_ROLE = "reaction-observer-read";
    static final String WRITE_ROLE = "reaction-observer-write";

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Present only when semantic authorization is active. Asking it while it is absent would be a wrong
     * answer rather than a missing one, so it is asked through a provider.
     */
    private final ObjectProvider<ServletSemanticAuthorization> semanticAuthorization;

    /** Whether the caller may read the graphs, the indexes, the names and the statistics. */
    public boolean canRead() {
        return isAuthorized(READ_ROLE, READ_OPERATION);
    }

    /** Whether the caller may trigger an aggregation. */
    public boolean canWrite() {
        return isAuthorized(WRITE_ROLE, WRITE_OPERATION);
    }

    private boolean isAuthorized(String simpleRole, String operation) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return hasAuthority(authentication, ROLE_PREFIX + simpleRole)
               || hasSemanticRole(authentication, operation);
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    /**
     * The semantic role, asked only of a token: {@link ServletSemanticAuthorization} reads the authentication
     * out of the security context and casts it, so asking it about a basic-auth request would not answer
     * false - it would fail.
     */
    private boolean hasSemanticRole(Authentication authentication, String operation) {
        if (!(authentication instanceof JeapAuthenticationToken)) {
            return false;
        }
        ServletSemanticAuthorization semantic = semanticAuthorization.getIfAvailable();
        return semantic != null && semantic.hasRole(RESOURCE, operation);
    }
}
