package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.web.models.graph.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphFingerprintCalculatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GraphFingerprintCalculator calculator = new GraphFingerprintCalculator(objectMapper);

    @Test
    void shouldCalculateValidFingerprintForGraphDto() {
        // Arrange: Create a simple GraphDto with one message node and one trigger edge
        MessageNodeDto messageNode = new MessageNodeDto(1L, "TestType", "v1");
        ReactionNodeDto reactionNode = new ReactionNodeDto(2L, "TestComponent");
        TriggerEdgeDto triggerEdge = new TriggerEdgeDto(1L, NodeDtoType.MESSAGE, 2L, 5);

        GraphDto graphDto = new GraphDto(List.of(messageNode, reactionNode), List.of(triggerEdge));

        // Act: Calculate fingerprint
        String fingerprint = calculator.calculate(graphDto);

        // Assert: Fingerprint is not null and has correct SHA-256 length
        assertThat(fingerprint).isNotNull();
        assertThat(fingerprint).hasSize(64); // SHA-256 hex string length
    }

    @Test
    void shouldReturnSameFingerprintForSameGraphDto() {
        // Arrange: Create two identical GraphDto instances
        MessageNodeDto messageNode = new MessageNodeDto(1L, "TestType", "v1");
        ReactionNodeDto reactionNode = new ReactionNodeDto(2L, "TestComponent");
        TriggerEdgeDto triggerEdge = new TriggerEdgeDto(1L, NodeDtoType.MESSAGE, 2L, 5);

        GraphDto graphDto1 = new GraphDto(List.of(messageNode, reactionNode), List.of(triggerEdge));
        GraphDto graphDto2 = new GraphDto(List.of(messageNode, reactionNode), List.of(triggerEdge));

        // Act: Calculate fingerprints
        String fingerprint1 = calculator.calculate(graphDto1);
        String fingerprint2 = calculator.calculate(graphDto2);

        // Assert: Fingerprints are equal
        assertThat(fingerprint1).isEqualTo(fingerprint2);
    }

    @Test
    void shouldReturnDifferentFingerprintForDifferentGraphDto() {
        // Arrange: Create two different GraphDto instances
        MessageNodeDto messageNode1 = new MessageNodeDto(1L, "TypeA", "v1");
        MessageNodeDto messageNode2 = new MessageNodeDto(2L, "TypeB", "v2");

        GraphDto graphDto1 = new GraphDto(List.of(messageNode1), List.of());
        GraphDto graphDto2 = new GraphDto(List.of(messageNode2), List.of());

        // Act: Calculate fingerprints
        String fingerprint1 = calculator.calculate(graphDto1);
        String fingerprint2 = calculator.calculate(graphDto2);

        // Assert: Fingerprints are different
        assertThat(fingerprint1).isNotEqualTo(fingerprint2);
    }
}
