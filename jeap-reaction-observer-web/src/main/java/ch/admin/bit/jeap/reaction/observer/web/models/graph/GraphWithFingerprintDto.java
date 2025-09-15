package ch.admin.bit.jeap.reaction.observer.web.models.graph;

public record GraphWithFingerprintDto(
        GraphDto graph,
        String fingerprint
) {}
