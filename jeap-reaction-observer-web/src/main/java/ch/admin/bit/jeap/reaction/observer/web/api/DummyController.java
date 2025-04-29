package ch.admin.bit.jeap.reaction.observer.web.api;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class DummyController {

    @PreAuthorize("hasAnyRole('reaction-observer-read','reaction-observer-write')")
    @Operation(summary = "Test Hello World")
    @GetMapping("/hello")
    public ResponseEntity<String> getHelloWorld() {
        log.info("Hello World");
        return ResponseEntity.ok("Hello World");
    }

}
