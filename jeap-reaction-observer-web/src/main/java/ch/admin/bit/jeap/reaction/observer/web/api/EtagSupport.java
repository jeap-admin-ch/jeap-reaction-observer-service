package ch.admin.bit.jeap.reaction.observer.web.api;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Entity tags and conditional requests for the API.
 * <p>
 * A tag is always {@code "sha256:<hex>"}. For a graph it is <b>the fingerprint the body already carries</b>, so
 * the tag of an index entry is the same string as the {@code ETag} of the resource it points at: a consumer can
 * compare without a request, and send what it stored as {@code If-None-Match} when it does fetch. For an index
 * it is the hash of the bytes that are written.
 * <p>
 * <b>The tag of a graph names the graph, not the bytes.</b> The fingerprint is computed over the canonicalized
 * graph, so it survives a change in property order - and it would not move if the envelope around the graph
 * ever gained a field. That is the price of a tag a consumer can precompute, and it holds as long as every
 * resource serves one representation, which they do.
 * <p>
 * It lives here rather than in each controller because a slip in the sequence - tagging something other than
 * what is written, or forgetting the cache directive on the {@code 304} - would break conditional requests on
 * one resource only, quietly.
 */
@Component
@RequiredArgsConstructor
public class EtagSupport {

    private final JsonMapper jsonMapper;

    /**
     * @param fingerprint a graph fingerprint, may be null
     * @return the entity tag for it, or null when there is none
     */
    public String entityTag(String fingerprint) {
        return fingerprint == null ? null : "\"sha256:" + fingerprint + "\"";
    }

    /**
     * Answers with the body and the given entity tag, or with {@code 304} when the caller already has it.
     *
     * @return the response, or null when the prepared {@code 304} is to be sent
     */
    public <T> ResponseEntity<T> respond(WebRequest request, T body, String entityTag) {
        if (isNotModified(request, entityTag)) {
            return null;
        }
        if (entityTag == null) {
            // Nothing to tag it with: an answer that is not addressable by a tag - an unknown name - is
            // served without one rather than with an empty header a consumer would try to send back.
            return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(body);
        }
        return ResponseEntity.ok()
                .eTag(entityTag)
                .cacheControl(CacheControl.noCache())
                .body(body);
    }

    /**
     * Answers with a body whose entity tag is the hash of its own serialized form - for an index, which has no
     * fingerprint of its own.
     * <p>
     * The bytes are serialized once here and written as they are, rather than serialized for the tag and
     * again by the message converter.
     *
     * @return the response, or null when the prepared {@code 304} is to be sent
     */
    public ResponseEntity<byte[]> respondSerialized(WebRequest request, Object body) {
        byte[] serialized = jsonMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        String entityTag = entityTag(DigestUtils.sha256Hex(serialized));
        if (isNotModified(request, entityTag)) {
            return null;
        }
        return ResponseEntity.ok()
                .eTag(entityTag)
                .cacheControl(CacheControl.noCache())
                .body(serialized);
    }

    /**
     * Evaluates {@code If-None-Match} and, when it matches, prepares the {@code 304} response.
     *
     * @return true when the caller should return null so that the prepared {@code 304} is sent
     */
    public boolean isNotModified(WebRequest request, String entityTag) {
        if (entityTag == null) {
            return false;
        }
        boolean notModified = request.checkNotModified(entityTag);
        if (notModified) {
            // The 304 is written by Spring, bypassing the ResponseEntity the 200 path builds - so the cache
            // directive has to be repeated here, or a consumer would see it on the 200 and not on the 304.
            setCacheControl(request);
        }
        return notModified;
    }

    private void setCacheControl(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            HttpServletResponse response = servletWebRequest.getResponse();
            if (response != null) {
                response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noCache().getHeaderValue());
            }
        }
    }
}
