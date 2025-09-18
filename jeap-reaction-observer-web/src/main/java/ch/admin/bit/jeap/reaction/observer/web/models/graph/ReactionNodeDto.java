package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;

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

    @Override
    public String getCanonicalId() {
        return NodeDtoType.REACTION + "-" + id;
    }
}