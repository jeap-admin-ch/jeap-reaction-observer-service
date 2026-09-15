package ch.admin.bit.jeap.reaction.observer.web.models.graph;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;

/**
 * @param median the median of the daily observation counts, the same number a trigger edge into this reaction
 *               carries - and the only place it appears for a reaction that no message triggered
 */
public record ReactionNodeDto(
        long id,
        String component,
        Integer median
) implements NodeDto {
    public static ReactionNodeDto from(Reaction reaction) {
        return new ReactionNodeDto(
                reaction.id(),
                reaction.component(),
                reaction.median()
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
