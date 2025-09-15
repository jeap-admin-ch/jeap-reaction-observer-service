package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ReactionNodeDto(
        long id,
        String component
) implements NodeDto {
    public static ReactionNodeDto from(Reaction reaction) {
        return new ReactionNodeDto(
                reaction.id(),
                reaction.component()
        );
    }

    @Override
    public long getId() {
        return id;
    }
}