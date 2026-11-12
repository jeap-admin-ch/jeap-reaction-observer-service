package ch.admin.bit.jeap.reaction.observer.web.models.graph.v2;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Interface;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * An interface node of the API v2: the node of an interface whose type the service does not present in its own
 * way, and the base of the nodes of those it does ({@link MessageNodeV2Dto}, {@link HttpOperationNodeV2Dto}).
 * <p>
 * Type and FQN are the identity of an interface, and every interface node has them. That makes this class what a
 * consumer reads a node of a kind it does not know as.
 */
@JsonSubTypes({
        @JsonSubTypes.Type(value = MessageNodeV2Dto.class, name = NodeV2Dto.MESSAGE),
        @JsonSubTypes.Type(value = HttpOperationNodeV2Dto.class, name = NodeV2Dto.HTTP_OPERATION)
})
@Schema(description = "An interface: what triggers a reaction or what a reaction acts on. Also what a node with "
                      + "an unknown nodeType is read as.")
@Getter
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InterfaceNodeV2Dto extends NodeV2Dto {

    @Schema(description = "The type of the interface, e.g. event, command or http_request. Free text: further "
                          + "types can be reported.", example = "http_request")
    private final String interfaceType;
    @Schema(description = "The fully qualified name of the interface. Together with interfaceType the identity of "
                          + "the interface.", example = "partner-service GET /api/partners/{}")
    private final String fqn;

    @Override
    public String getNodeType() {
        return INTERFACE;
    }

    public static InterfaceNodeV2Dto from(Interface iface) {
        return builder()
                .id(NodeV2Id.ofInterface(iface.id()))
                .interfaceType(iface.type())
                .fqn(iface.fqn())
                .build();
    }
}
