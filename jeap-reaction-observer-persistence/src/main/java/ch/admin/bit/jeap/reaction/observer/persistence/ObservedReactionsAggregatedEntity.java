package ch.admin.bit.jeap.reaction.observer.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Objects;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "observed_reactions_aggregated")
@Getter
@NoArgsConstructor(access = PROTECTED)  // for JPA
@ToString
class ObservedReactionsAggregatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "observed_reactions_aggregated_sequence")
    @SequenceGenerator(name = "observed_reactions_aggregated_sequence", sequenceName = "observed_reactions_aggregated_sequence", allocationSize = 10)
    @Column(name = "id")
    private Long id;

    @Column(name = "reaction_fk")
    private Long reactionFk;

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

    @Column(name = "date")
    private LocalDate date;

    @Setter
    @Column(name = "count")
    private int count;

    @Builder
    public ObservedReactionsAggregatedEntity(Long reactionFk, String component, String triggerType, String triggerFqn, String actionType, String actionFqn, LocalDate date, int count) {
        this.reactionFk = reactionFk;
        this.component = component;
        this.triggerType = triggerType;
        this.triggerFqn = triggerFqn;
        this.actionType = actionType;
        this.actionFqn = actionFqn;
        this.date = date;
        this.count = count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObservedReactionsAggregatedEntity that = (ObservedReactionsAggregatedEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}