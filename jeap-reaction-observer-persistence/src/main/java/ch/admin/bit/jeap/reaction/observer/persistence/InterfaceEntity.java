package ch.admin.bit.jeap.reaction.observer.persistence;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.ZonedDateTime;
import java.util.Objects;

import static lombok.AccessLevel.PROTECTED;

@NoArgsConstructor(access = PROTECTED)
@Getter
@Entity
@Table(name = "interface")
public class InterfaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "interface_sequence")
    @SequenceGenerator(name = "interface_sequence", sequenceName = "interface_sequence", allocationSize = 10)
    @Column(name = "id")
    private Long id;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "fqn", nullable = false)
    private String fqn;

    @Builder
    InterfaceEntity(@NonNull String type, @NonNull String fqn) {
        this.type = type;
        this.fqn = fqn;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        InterfaceEntity that = (InterfaceEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

