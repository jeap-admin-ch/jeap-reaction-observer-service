package ch.admin.bit.jeap.reaction.observer.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
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

    @Column(name = "system")
    private String system;

    @Column(name = "component")
    private String component;

    @Column(name = "trigger_id")
    private String triggerId;

    @Column(name = "trigger_type")
    private String triggerType;

    @Column(name = "trigger_fqn")
    private String triggerFqn;

    @Column(name = "action_id")
    private String actionId;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "action_fqn")
    private String actionFqn;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "reaction")
    private List<ActionEntity> actions = new ArrayList<>();

    @Column(name = "identified_at")
    private ZonedDateTime identifiedAt;

    @Builder
    private ReactionEntity(@NonNull String reactionId, String system, @NonNull String component,
                           String triggerId, String triggerType, String triggerFqn,
                           String actionId, String actionType, String actionFqn,
                           @NonNull ZonedDateTime identifiedAt) {
        this.reactionId = reactionId;
        this.system = system;
        this.component = component;
        this.identifiedAt = identifiedAt;
        this.triggerId = triggerId;
        this.triggerType = triggerType;
        this.triggerFqn = triggerFqn;
        this.actionId = actionId;
        this.actionType = actionType;
        this.actionFqn = actionFqn;
    }

    public void addAction(ActionEntity action) {
        if (actions == null) {
            actions = new ArrayList<>();
        }
        action.setReaction(this);
        actions.add(action);
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
