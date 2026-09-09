package ch.admin.bit.jeap.reaction.observer.web.service;

/**
 * A graph whose fingerprint could not be computed - which means it could not be serialized or canonicalized.
 * <p>
 * There is nothing a caller can do about it, so it is unchecked; it is its own type rather than a plain
 * {@code RuntimeException} so that a log line, or a handler, can tell it from anything else that goes wrong
 * while a request is answered.
 */
public class FingerprintCalculationException extends RuntimeException {

    FingerprintCalculationException(Throwable cause) {
        super("The fingerprint of the graph could not be calculated", cause);
    }
}
