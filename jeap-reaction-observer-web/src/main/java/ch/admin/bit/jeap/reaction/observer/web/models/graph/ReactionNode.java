package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReactionNode(
        @JsonProperty("nodeType") NodeType nodeType,
        long id,
        String component
) implements Node {
    @Override
    public long getId() {
        return id;
    }
}