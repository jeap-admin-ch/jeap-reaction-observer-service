package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.ComponentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "OIDC")
public class ComponentController {

    private final ComponentRepository componentRepository;

    @PreAuthorize("hasRole('reactions', 'read')")
    @Operation(summary = "Get component names", description = "Get all component names for which reactions are observed.")
    @GetMapping("/components/names")
    public ResponseEntity<List<String>> getComponentNames() {
        return ResponseEntity.ok(componentRepository.getComponentNames().stream().filter(Objects::nonNull).toList());
    }
}
