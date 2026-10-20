package ch.admin.bit.jeap.reaction.observer.web.api;

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
 * That every handler of the API is authorized with one of its semantic roles.
 * <p>
 * The chain requires authentication, while the handler decides which semantic role is needed. This test reads
 * the handlers rather than a list so a new endpoint cannot accidentally omit that authorization.
 */
class ReactionsApiRoleCoverageTest {

    private static final String API_PACKAGE = "ch.admin.bit.jeap.reaction.observer.web.api";
    private static final Set<String> AUTHORIZATION_EXPRESSIONS = Set.of(
            "hasRole('reactions', 'read')",
            "hasRole('reactions', 'write')");

    @Test
    void everyApiHandler_hasASemanticRoleAuthorizationCheck() {
        Set<String> unauthorized = new TreeSet<>();

        for (Class<?> controller : apiControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isHandler(method)) {
                    continue;
                }
                PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(method,
                        PreAuthorize.class);
                if (preAuthorize == null || !AUTHORIZATION_EXPRESSIONS.contains(preAuthorize.value())) {
                    unauthorized.add(controller.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertThat(unauthorized)
                .describedAs("every handler under /api needs a semantic read/write @PreAuthorize role check")
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
