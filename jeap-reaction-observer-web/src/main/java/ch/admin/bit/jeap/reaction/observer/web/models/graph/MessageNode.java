package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageNode(
        @JsonProperty("nodeType") NodeType nodeType,
        long id,
        String messageKey,
        String messageType,
        String variant
) implements Node {
    @Override
    public long getId() {
        return id;
    }
}
