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
@NamedEntityGraph(
        name = "Reaction.withActions",
        attributeNodes = @NamedAttributeNode("actions")
)
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "interface_id")
    private InterfaceEntity triggerInterface;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "reaction")
    private List<ActionEntity> actions = new ArrayList<>();

    @Column(name = "identified_at")
    private ZonedDateTime identifiedAt;

    @Builder
    private ReactionEntity(@NonNull String reactionId, String system, @NonNull String component,
                           String triggerId, InterfaceEntity triggerInterface,
                           @NonNull ZonedDateTime identifiedAt) {
        this.reactionId = reactionId;
        this.system = system;
        this.component = component;
        this.identifiedAt = identifiedAt;
        this.triggerId = triggerId;
        this.triggerInterface = triggerInterface;
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
