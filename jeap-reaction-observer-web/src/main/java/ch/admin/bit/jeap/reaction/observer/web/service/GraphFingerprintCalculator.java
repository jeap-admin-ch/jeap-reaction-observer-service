package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.web.models.graph.EdgeDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.NodeDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GraphFingerprintCalculator {

    private final JsonMapper jsonMapper;

    /**
     * One fingerprint over several of them, for a resource that answers more than one graph at once - the
     * variants of a message type.
     * <p>
     * Over the keys as well as the fingerprints, and in a defined order, so that a variant appearing or
     * disappearing moves the value even when no graph changed.
     *
     * @param fingerprintsByKey the fingerprint of each part, keyed by how the resource names it
     */
    public String combine(Map<String, String> fingerprintsByKey) {
        String canonical = new TreeMap<>(fingerprintsByKey).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        return DigestUtils.sha256Hex(canonical);
    }

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
            String json = jsonMapper.writeValueAsString(sortedDto);
            String canonicalJson = new JsonCanonicalizer(json).getEncodedString();

            // Return SHA-256 fingerprint
            return DigestUtils.sha256Hex(canonicalJson);
        } catch (Exception e) {
            throw new RuntimeException("Fingerprint calculation failed", e);
        }
    }
}
