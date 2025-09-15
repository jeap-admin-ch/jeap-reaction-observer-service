package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.web.models.graph.GraphDto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


@Component
@RequiredArgsConstructor
public class GraphFingerprintCalculator {

    private final ObjectMapper objectMapper;

    public String calculate(GraphDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            return sha256Hex(json);
        } catch (Exception e) {
            throw new RuntimeException("Fingerprint calculation failed", e);
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
