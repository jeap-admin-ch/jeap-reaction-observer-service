package ch.admin.bit.jeap.reaction.observer.web.models.graph.v2;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * The node of a message of the API v2: an interface of the type {@code event} or {@code command}.
 */
@Schema(description = "A message: an interface of the type event or command")
@Getter
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MessageNodeV2Dto extends InterfaceNodeV2Dto {

    @Schema(description = "The message type")
    private final String messageType;
    @Schema(description = "The variant of the message type; null when it has none", nullable = true)
    private final String variant;

    @Override
    public String getNodeType() {
        return MESSAGE;
    }

    public static MessageNodeV2Dto from(Message message) {
        return builder()
                .id(NodeV2Id.ofInterface(message.id()))
                .interfaceType(message.type())
                .fqn(message.fqn())
                .messageType(message.messageType())
                .variant(message.variant())
                .build();
    }
}
