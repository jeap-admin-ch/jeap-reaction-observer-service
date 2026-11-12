package ch.admin.bit.jeap.reaction.observer.web.models.graph.v2;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * A reaction node of the API v2.
 */
@Schema(description = "A reaction: what a component does when one interface triggers it")
@Getter
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ReactionNodeV2Dto extends NodeV2Dto {

    static final String STATUS_ACTIVE = "ACTIVE";

    @Schema(description = "The component reacting")
    private final String component;
    @Schema(description = "The system of the component")
    private final String system;
    @Schema(description = "Median of the daily observation counts within the statistics window. Always a "
                          + "number, from 0: a reaction is in a graph only if it was observed within the window.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer median;
    @Schema(description = "ACTIVE or SUPERSEDED. Not a closed set: treat an unknown value as unknown.",
            example = "ACTIVE")
    private final String status;
    @Schema(description = "Reaction model version of the last day the reaction was observed")
    private final int reactionModelVersion;

    @Override
    public String getNodeType() {
        return REACTION;
    }

    /**
     * Status and reaction model version are part of the contract and carry their neutral values until the
     * service derives them.
     */
    public static ReactionNodeV2Dto from(Reaction reaction) {
        return builder()
                .id(NodeV2Id.ofReaction(reaction.id()))
                .component(reaction.component())
                .system(reaction.system())
                .median(reaction.median())
                .status(STATUS_ACTIVE)
                .reactionModelVersion(1)
                .build();
    }
}
