package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageNodeDto(
        long id,
        String messageType,
        String variant
) implements NodeDto {
    public static MessageNodeDto from(Message message) {
        return new MessageNodeDto(
                message.id(),
                message.messageType(),
                message.variant()
        );
    }

    @Override
    public long getId() {
        return id;
    }
}
