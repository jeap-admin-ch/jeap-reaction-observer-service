package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReaction;
import ch.admin.bit.jeap.reaction.observer.domain.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.Timeframe;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

import static lombok.AccessLevel.PROTECTED;

@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
@Table(name = "observed_reaction")
class ObservedReactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "observed_reaction_sequence")
    @SequenceGenerator(name = "observed_reaction_sequence", sequenceName = "observed_reaction_sequence", allocationSize = 10)
    @Column(name = "id")
    private Long id;

    @Column(name = "reaction_fk")
    private Long reactionFk;

    @Column(name = "idempotence_id")
    private String idempotenceId;

    @Column(name = "timeframe_start")
    private ZonedDateTime timeframeStart;

    @Column(name = "timeframe_end")
    private ZonedDateTime timeframeEnd;

    @Column(name = "observation_date")
    private LocalDate observationDate;

    @Column(name = "count")
    private int count;

    @Builder
    private ObservedReactionEntity(Long reactionFk, String idempotenceId, ZonedDateTime timeframeStart, ZonedDateTime timeframeEnd, LocalDate observationDate, int count) {
        this.reactionFk = reactionFk;
        this.idempotenceId = idempotenceId;
        this.timeframeStart = timeframeStart;
        this.timeframeEnd = timeframeEnd;
        this.observationDate = observationDate;
        this.count = count;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ObservedReactionEntity that = (ObservedReactionEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public ObservedReaction toDomainObject(Reaction reaction) {
        var timeframe = new Timeframe(timeframeStart, timeframeEnd);
        return new ObservedReaction(reaction.component(), reaction.reactionId(), timeframe, count);
    }
}
