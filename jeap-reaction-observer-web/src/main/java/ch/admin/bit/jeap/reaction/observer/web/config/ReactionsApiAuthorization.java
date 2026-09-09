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
 * Which of the two ways of authenticating a request came in by, and whether it may do what it asks.
 * <p>
 * <b>One mechanism, one rule:</b>
 *
 * <table>
 *     <caption>The rules</caption>
 *     <tr><th>Authenticated by</th><th>Authorized by</th></tr>
 *     <tr><td>HTTP Basic</td><td>the in-memory user's role, {@code reaction-observer-read} / {@code -write}</td></tr>
 *     <tr><td>A bearer token</td><td>the semantic role {@code <system-name>_@reactions_#read} / {@code _#write}</td></tr>
 * </table>
 *
 * <b>No tenant part</b> on the semantic role, and that is deliberate: a tenant says which mandant may
 * exercise a role, and there is no such division here. A system's subgraph is cut out of one graph and
 * carries the messages of other systems by construction, and a consumer that documents a landscape reads
 * every system of it.
 * <p>
 * <b>A token is authorized by its semantic role and by nothing else.</b> The simple role a basic-auth user
 * holds is not accepted from a token: one credential, one role model, so that what a grant means cannot
 * depend on how the caller happened to connect.
 * <p>
 * <b>Why this bean exists at all - and why it is only this.</b> Everything here would be an expression on the
 * handler methods if a single expression could serve both mechanisms, and none can:
 * {@code hasRole('reactions', 'read')} exists only on the expression root the jEAP security starter installs
 * <em>for a {@code JeapAuthenticationToken}</em> ({@code SemanticMethodSecurityExpressionHandler}), so on a
 * basic-auth request the expression would not resolve at all and the request would fail rather than be
 * refused. Hence one bean, two branches, and nothing else in it.
 */
@Component("reactionsApiAuthorization")
@RequiredArgsConstructor
public class ReactionsApiAuthorization {

    /** The resource part of the semantic role - what this API is about. */
    public static final String RESOURCE = "reactions";

    public static final String READ_OPERATION = "read";
    public static final String WRITE_OPERATION = "write";

    /** The roles of the two basic-auth users. */
    static final String READ_ROLE = "reaction-observer-read";
    static final String WRITE_ROLE = "reaction-observer-write";

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Present exactly when semantic authorization is active, which
     * {@code jeap.security.oauth2.resourceserver.system-name} decides. A bearer token cannot be authenticated
     * at all unless the resource server is configured, and {@code WebSecurityConfig} refuses to start a
     * resource server without a system name - so where a token can arrive, this is here.
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

    private boolean isAuthorized(String basicAuthRole, String operation) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (authentication instanceof JeapAuthenticationToken) {
            // ServletSemanticAuthorization reads the token out of the security context itself
            ServletSemanticAuthorization semantic = semanticAuthorization.getIfAvailable();
            return semantic != null && semantic.hasRole(RESOURCE, operation);
        }
        return hasAuthority(authentication, ROLE_PREFIX + basicAuthRole);
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
