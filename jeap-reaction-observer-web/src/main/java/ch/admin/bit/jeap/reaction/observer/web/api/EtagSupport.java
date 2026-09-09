package ch.admin.bit.jeap.reaction.observer.web.api;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.jspecify.annotations.Nullable;
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
 * A tag is always {@code "sha256:<hex>"}. For a graph it is <b>the fingerprint the body also carries</b>, so
 * the tag of an index entry is the same string as the {@code ETag} of the resource it points at: a consumer can
 * compare without a request, and send what it stored as {@code If-None-Match} when it does fetch. For an index
 * it is the hash of the bytes that are written, computed once when the index is built.
 * <p>
 * <b>The tag of a graph names the graph, not the bytes.</b> The fingerprint is computed over the canonicalized
 * graph, so it survives a change in property order - and it would not move if the envelope around the graph
 * ever gained a field. That is the price of a tag a consumer can precompute, and it holds as long as every
 * resource serves one representation, which they do.
 * <p>
 * It lives here rather than in each controller because a slip in the sequence - tagging something other than
 * what is written, or forgetting the cache directive on the {@code 304} - would break conditional requests on
 * one resource only, quietly.
 * <p>
 * <b>The contract of the two {@code respond} methods:</b> they answer {@code null} when the caller is to
 * return {@code null} itself, so that the {@code 304} they have already prepared on the response is the
 * answer. A handler therefore reads {@code return etagSupport.respond(...)} and nothing else.
 */
@Component
@RequiredArgsConstructor
public class EtagSupport {

    private final JsonMapper jsonMapper;

    /**
     * @param fingerprint a graph fingerprint, may be null
     * @return the entity tag for it, or null when there is none
     */
    public @Nullable String entityTag(@Nullable String fingerprint) {
        return fingerprint == null ? null : "\"sha256:" + fingerprint + "\"";
    }

    /** The entity tag of an already serialized body, so that the tag names exactly the bytes on the wire. */
    public String entityTagOf(byte[] serializedBody) {
        return entityTag(DigestUtils.sha256Hex(serializedBody));
    }

    /**
     * Serializes a body to the bytes that will be written, so that it can be tagged and kept - see
     * {@code GraphSnapshotFactory}, which does both once per refresh.
     */
    public byte[] serialize(Object body) {
        return jsonMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Answers with the body and the given entity tag, or with {@code 304} when the caller already has it.
     *
     * @param entityTag the tag of this body. Null only where the resource has none to offer - an answer that
     *                  is not addressable by a tag - and then the answer carries no {@code ETag} rather than
     *                  an empty one a consumer would send back
     * @return the response, or <b>null when the prepared {@code 304} is to be sent</b>
     */
    public <T> @Nullable ResponseEntity<T> respond(WebRequest request, T body, @Nullable String entityTag) {
        if (isNotModified(request, entityTag)) {
            return null;
        }
        return ok(body, entityTag);
    }

    /**
     * The {@code 200} of a resource whose condition has already been evaluated with
     * {@link #isNotModified(WebRequest, String)} - so that no request evaluates it twice.
     */
    public <T> ResponseEntity<T> ok(T body, @Nullable String entityTag) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(CacheControl.noCache());
        if (entityTag != null) {
            response.eTag(entityTag);
        }
        return response.body(body);
    }

    /**
     * Evaluates {@code If-None-Match} and, when it matches, prepares the {@code 304} response.
     * <p>
     * <b>Call it once per request.</b> {@code WebRequest.checkNotModified} writes the tag it was given onto
     * the response, so a second call with a different tag would leave the response carrying the wrong one.
     *
     * @return true when the caller should return null so that the prepared {@code 304} is sent
     */
    public boolean isNotModified(WebRequest request, @Nullable String entityTag) {
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
