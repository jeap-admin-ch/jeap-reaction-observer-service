package ch.admin.bit.jeap.reaction.observer.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;

import static lombok.AccessLevel.PROTECTED;

@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
@Table(name = "observation_property")
class ObservationProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "observation_property_sequence")
    @SequenceGenerator(name = "observation_property_sequence", sequenceName = "observation_property_sequence", allocationSize = 10)
    @Column(name = "id")
    private Long id;

    @Column(name = "reaction_trigger_fk")
    private Long reactionTriggerFk;

    @Column(name = "reaction_action_fk")
    private Long reactionActionFk;

    @Column(name = "property_key")
    private String key;

    @Column(name = "property_value")
    private String value;

    @Builder
    private ObservationProperty(Long reactionTriggerFk, Long reactionActionFk, @NonNull String key, @NonNull String value) {
        this.reactionTriggerFk = reactionTriggerFk;
        this.reactionActionFk = reactionActionFk;
        this.key = key;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ObservationProperty that = (ObservationProperty) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
