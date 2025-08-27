package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "edgeType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TriggerEdge.class, name = "TRIGGER"),
        @JsonSubTypes.Type(value = ActionEdge.class, name = "ACTION")
})
public interface Edge {
}
