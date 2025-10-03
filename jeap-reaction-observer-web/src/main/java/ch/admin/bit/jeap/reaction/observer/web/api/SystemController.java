package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.SystemRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class SystemController {

    private final SystemRepository systemRepository;

    @PreAuthorize("hasAnyRole('reaction-observer-read')")
    @Operation(summary = "Get system names", description = "Get all system names for which reactions are observed.")
    @GetMapping("/systems/names")
    public ResponseEntity<List<String>> getSystemNames() {
        return ResponseEntity.ok(systemRepository.getSystemNames().stream().filter(Objects::nonNull).toList());
    }

}
