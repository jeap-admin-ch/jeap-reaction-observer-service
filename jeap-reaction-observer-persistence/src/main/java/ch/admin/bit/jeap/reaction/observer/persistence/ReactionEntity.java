package ch.admin.bit.jeap.reaction.observer.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.Objects;

import static lombok.AccessLevel.PROTECTED;

@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
@Table(name = "reaction")
class ReactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reaction_sequence")
    @SequenceGenerator(name = "reaction_sequence", sequenceName = "reaction_sequence", allocationSize = 10)
    @Column(name = "id")
    private Long id;

    @Column(name = "reaction_id")
    private String reactionId;

    @Column(name = "component")
    private String component;

    @Column(name = "trigger_type")
    private String triggerType;

    @Column(name = "trigger_fqn")
    private String triggerFqn;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "action_fqn")
    private String actionFqn;

    @Column(name = "identified_at")
    private ZonedDateTime identifiedAt;

    @Builder
    private ReactionEntity(@NonNull String reactionId, @NonNull String component,
                           String triggerType, String triggerFqn,
                           String actionType, String actionFqn,
                           @NonNull ZonedDateTime identifiedAt) {
        this.reactionId = reactionId;
        this.component = component;
        this.identifiedAt = identifiedAt;
        this.triggerType = triggerType;
        this.triggerFqn = triggerFqn;
        this.actionType = actionType;
        this.actionFqn = actionFqn;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ReactionEntity that = (ReactionEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
