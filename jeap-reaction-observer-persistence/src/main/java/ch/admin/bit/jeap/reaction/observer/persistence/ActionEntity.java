package ch.admin.bit.jeap.reaction.observer.persistence;

import jakarta.persistence.*;
import lombok.*;

import static lombok.AccessLevel.PROTECTED;

@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
@Table(name = "action")
public class ActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "action_sequence")
    @SequenceGenerator(name = "action_sequence", sequenceName = "action_sequence", allocationSize = 10)
    @Column(name = "id")
    private Long id;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reaction_id")
    @Setter
    private ReactionEntity reaction;

    @Column(name = "action_id")
    private String actionId;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "action_fqn")
    private String actionFqn;

    @Builder
    public ActionEntity(ReactionEntity reaction, String actionId, String actionType, String actionFqn) {
        this.reaction = reaction;
        this.actionId = actionId;
        this.actionType = actionType;
        this.actionFqn = actionFqn;
    }
}
