package ch.admin.bit.jeap.reaction.observer.web.service;

import ch.admin.bit.jeap.reaction.observer.web.models.graph.*;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class GraphFingerprintCalculator {

    private final ObjectMapper objectMapper;

    public String calculate(GraphDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            String canonicalJson = new JsonCanonicalizer(json).getEncodedString();
            return DigestUtils.sha256Hex(canonicalJson);
        } catch (Exception e) {
            throw new RuntimeException("Fingerprint calculation failed", e);
        }
    }
}
