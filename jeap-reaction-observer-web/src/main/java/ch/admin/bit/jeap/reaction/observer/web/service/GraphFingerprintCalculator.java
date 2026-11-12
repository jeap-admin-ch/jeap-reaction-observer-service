package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.web.models.graph.EdgeDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.NodeDto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.EdgeV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.GraphV2Dto;
import ch.admin.bit.jeap.reaction.observer.web.models.graph.v2.NodeV2Dto;
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
        // Sort nodes and edges by canonical ID
        var sortedNodes = dto.nodes().stream()
                .sorted(Comparator.comparing(NodeDto::getCanonicalId))
                .toList();
        var sortedEdges = dto.edges().stream()
                .sorted(Comparator.comparing(EdgeDto::getCanonicalId))
                .toList();
        return fingerprintOf(new GraphDto(sortedNodes, sortedEdges));
    }

    /**
     * The fingerprint of a graph of the API v2. Nodes are ordered by their id, edges by type, source and target,
     * and every field of a node is part of the fingerprint - a reaction that changes its status changes it.
     */
    public String calculate(GraphV2Dto dto) {
        var sortedNodes = dto.nodes().stream()
                .sorted(Comparator.comparing(NodeV2Dto::getId))
                .toList();
        var sortedEdges = dto.edges().stream()
                .sorted(Comparator.comparing(EdgeV2Dto::edgeType)
                        .thenComparing(EdgeV2Dto::sourceId)
                        .thenComparing(EdgeV2Dto::targetId))
                .toList();
        return fingerprintOf(new GraphV2Dto(sortedNodes, sortedEdges));
    }

    private String fingerprintOf(Object sortedGraph) {
        try {
            // Serialize and canonicalize
            String json = jsonMapper.writeValueAsString(sortedGraph);
            String canonicalJson = new JsonCanonicalizer(json).getEncodedString();

            // Return SHA-256 fingerprint
            return DigestUtils.sha256Hex(canonicalJson);
        } catch (Exception e) {
            throw new FingerprintCalculationException(e);
        }
    }
}
