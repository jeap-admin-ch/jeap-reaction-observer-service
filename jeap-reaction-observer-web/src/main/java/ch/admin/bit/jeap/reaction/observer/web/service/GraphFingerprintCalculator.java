package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.web.models.graph.EdgeDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.NodeDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class GraphFingerprintCalculator {

    private final ObjectMapper objectMapper;

    public String calculate(GraphDto dto) {
        try {
            // Sort nodes by canonical ID
            var sortedNodes = dto.nodes().stream()
                    .sorted(Comparator.comparing(NodeDto::getCanonicalId))
                    .toList();

            // Sort edges by canonical ID
            var sortedEdges = dto.edges().stream()
                    .sorted(Comparator.comparing(EdgeDto::getCanonicalId))
                    .toList();

            var sortedDto = new GraphDto(sortedNodes, sortedEdges);

            // Serialize and canonicalize
            String json = objectMapper.writeValueAsString(sortedDto);
            String canonicalJson = new JsonCanonicalizer(json).getEncodedString();

            // Return SHA-256 fingerprint
            return DigestUtils.sha256Hex(canonicalJson);
        } catch (Exception e) {
            throw new RuntimeException("Fingerprint calculation failed", e);
        }
    }
}
