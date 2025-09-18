package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.web.models.graph.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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
    void shouldReturnSameFingerprintForSameGraphWithDifferentOrder() {
        MessageNodeDto messageNode = new MessageNodeDto(1L, "TestType", "v1");
        ReactionNodeDto reactionNode = new ReactionNodeDto(2L, "TestComponent");
        TriggerEdgeDto triggerEdge = new TriggerEdgeDto(1L, NodeDtoType.MESSAGE, 2L, 5);

        // Original order
        GraphDto graphDto1 = new GraphDto(List.of(messageNode, reactionNode), List.of(triggerEdge));

        // Reversed order
        GraphDto graphDto2 = new GraphDto(List.of(reactionNode, messageNode), List.of(triggerEdge));

        String fingerprint1 = calculator.calculate(graphDto1);
        String fingerprint2 = calculator.calculate(graphDto2);

        assertThat(fingerprint1).isEqualTo(fingerprint2);
    }

    @Test
    void shouldReturnSameFingerprintForSameEdgesInDifferentOrder() {
        MessageNodeDto messageNode = new MessageNodeDto(1L, "TypeA", "v1");
        ReactionNodeDto reactionNode = new ReactionNodeDto(2L, "ComponentA");
        TriggerEdgeDto edge1 = new TriggerEdgeDto(1L, NodeDtoType.MESSAGE, 2L, 5);
        ActionEdgeDto edge2 = new ActionEdgeDto(2L, 1L, NodeDtoType.MESSAGE);

        GraphDto graphDto1 = new GraphDto(List.of(messageNode, reactionNode), List.of(edge1, edge2));
        GraphDto graphDto2 = new GraphDto(List.of(messageNode, reactionNode), List.of(edge2, edge1));

        String fingerprint1 = calculator.calculate(graphDto1);
        String fingerprint2 = calculator.calculate(graphDto2);

        assertThat(fingerprint1).isEqualTo(fingerprint2);
    }

    @Test
    void shouldReturnDifferentFingerprintForDifferentMedian() {
        MessageNodeDto messageNode = new MessageNodeDto(1L, "TestType", "v1");
        ReactionNodeDto reactionNode = new ReactionNodeDto(2L, "TestComponent");

        TriggerEdgeDto edge1 = new TriggerEdgeDto(1L, NodeDtoType.MESSAGE, 2L, 5);
        TriggerEdgeDto edge2 = new TriggerEdgeDto(1L, NodeDtoType.MESSAGE, 2L, 6); // different median

        GraphDto graphDto1 = new GraphDto(List.of(messageNode, reactionNode), List.of(edge1));
        GraphDto graphDto2 = new GraphDto(List.of(messageNode, reactionNode), List.of(edge2));

        String fingerprint1 = calculator.calculate(graphDto1);
        String fingerprint2 = calculator.calculate(graphDto2);

        assertThat(fingerprint1).isNotEqualTo(fingerprint2);
    }

    @Test
    void shouldReturnValidFingerprintForEmptyGraph() {
        GraphDto emptyGraph = new GraphDto(List.of(), List.of());

        String fingerprint = calculator.calculate(emptyGraph);

        assertThat(fingerprint).isNotNull();
        assertThat(fingerprint).hasSize(64);
    }

    private GraphDto createSampleGraph() {
        MessageNodeDto messageNode = new MessageNodeDto(1L, "TestType", "v1");
        ReactionNodeDto reactionNode = new ReactionNodeDto(2L, "TestComponent");
        TriggerEdgeDto triggerEdge = new TriggerEdgeDto(1L, NodeDtoType.MESSAGE, 2L, 5);
        return new GraphDto(List.of(messageNode, reactionNode), List.of(triggerEdge));
    }
}
