package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.web.models.graph.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.erdtman.jcs.JsonCanonicalizer;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphFingerprintCalculatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GraphFingerprintCalculator calculator = new GraphFingerprintCalculator(objectMapper);

    @Test
    void shouldCalculateValidFingerprintForGraphDto() {
        GraphDto graphDto = createSampleGraph();

        String fingerprint = calculator.calculate(graphDto);

        assertThat(fingerprint).isNotNull();
        assertThat(fingerprint).hasSize(64); // SHA-256 hex string length
    }

    @Test
    void shouldReturnSameFingerprintForSameGraphDto() {
        GraphDto graphDto1 = createSampleGraph();
        GraphDto graphDto2 = createSampleGraph();

        String fingerprint1 = calculator.calculate(graphDto1);
        String fingerprint2 = calculator.calculate(graphDto2);

        assertThat(fingerprint1).isEqualTo(fingerprint2);
    }

    @Test
    void shouldReturnDifferentFingerprintForDifferentGraphDto() {
        MessageNodeDto node1 = new MessageNodeDto(1L, "TypeA", "v1");
        MessageNodeDto node2 = new MessageNodeDto(2L, "TypeB", "v2");

        GraphDto graphDto1 = new GraphDto(List.of(node1), List.of());
        GraphDto graphDto2 = new GraphDto(List.of(node2), List.of());

        String fingerprint1 = calculator.calculate(graphDto1);
        String fingerprint2 = calculator.calculate(graphDto2);

        assertThat(fingerprint1).isNotEqualTo(fingerprint2);
    }

    private GraphDto createSampleGraph() {
        MessageNodeDto messageNode = new MessageNodeDto(1L, "TestType", "v1");
        ReactionNodeDto reactionNode = new ReactionNodeDto(2L, "TestComponent");
        TriggerEdgeDto triggerEdge = new TriggerEdgeDto(1L, NodeDtoType.MESSAGE, 2L, 5);
        return new GraphDto(List.of(messageNode, reactionNode), List.of(triggerEdge));
    }
}
