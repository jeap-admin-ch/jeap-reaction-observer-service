package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.web.config.ReactionsApiAuthorization;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That every handler of the API is authorized, and authorized through {@link ReactionsApiAuthorization}.
 * <p>
 * The chain lets every {@code GET} under {@code /api} through and leaves the decision to the handler, which is
 * what lets one rule serve both HTTP Basic and a bearer token. The price of that is exactly this: <b>a handler
 * added without the annotation would be public</b>, and nothing else would say so. Hence a test that reads the
 * handlers rather than a list.
 * <p>
 * It also insists on the expression being the bean's: {@code hasRole('reactions', 'read')} written directly
 * into an annotation would work on an instance with semantic authorization and fail on every other one - see
 * {@link ReactionsApiAuthorization}.
 */
class ReactionsApiRoleCoverageTest {

    private static final String API_PACKAGE = "ch.admin.bit.jeap.reaction.observer.web.api";
    private static final String AUTHORIZATION_BEAN = "@reactionsApiAuthorization.";

    @Test
    void everyApiHandler_isAuthorizedThroughTheAuthorizationBean() {
        Set<String> unauthorized = new TreeSet<>();

        for (Class<?> controller : apiControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isHandler(method)) {
                    continue;
                }
                PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(method,
                        PreAuthorize.class);
                if (preAuthorize == null || !preAuthorize.value().startsWith(AUTHORIZATION_BEAN)) {
                    unauthorized.add(controller.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertThat(unauthorized)
                .describedAs("every handler under /api needs @PreAuthorize(\"" + AUTHORIZATION_BEAN
                             + "canRead()\") or ...canWrite(), or it is reachable by anyone who can reach "
                             + "the service")
                .isEmpty();
    }

    @Test
    void thereAreApiControllersToCheck() {
        // Guards the test itself: a scan that finds nothing would pass the assertion above silently
        assertThat(apiControllers()).hasSizeGreaterThanOrEqualTo(5);
    }

    private static Set<Class<?>> apiControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<Class<?>> controllers = new java.util.LinkedHashSet<>();
        scanner.findCandidateComponents(API_PACKAGE).forEach(candidate ->
                controllers.add(resolve(candidate.getBeanClassName())));
        return controllers;
    }

    private static boolean isHandler(Method method) {
        return Modifier.isPublic(method.getModifiers())
               && AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) != null;
    }

    private static Class<?> resolve(String className) {
        try {
            return ClassUtils.forName(className, ReactionsApiRoleCoverageTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load the controller " + className, e);
        }
    }
}
