package ch.admin.bit.jeap.reaction.observer.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;

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

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reaction_id")
    @Setter
    private ReactionEntity reaction;

    @Column(name = "action_id")
    private String actionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interface_id")
    private InterfaceEntity actionInterface;


    @Builder
    public ActionEntity(ReactionEntity reaction, String actionId, InterfaceEntity actionInterface) {
        this.reaction = reaction;
        this.actionId = actionId;
        this.actionInterface = actionInterface;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ActionEntity that = (ActionEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
