package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "nodeType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MessageNode.class, name = "MESSAGE"),
        @JsonSubTypes.Type(value = ReactionNode.class, name = "REACTION")
})
public interface Node {
    long getId();
}
