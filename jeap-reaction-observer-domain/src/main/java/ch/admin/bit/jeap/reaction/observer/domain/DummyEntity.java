package ch.admin.bit.jeap.reaction.observer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

/**
 * Represents a dummy entity in the domain layer.
 * This class is a JPA entity and is used to map to a database table.
 * It includes an ID and a name as its attributes.
 */
@AllArgsConstructor(access = PRIVATE) // Generates a private all-args constructor.
@NoArgsConstructor(access = PROTECTED) // Generates a protected no-args constructor for JPA.
@ToString // Generates a toString method for the class.
@Getter // Generates getter methods for all fields.
@Entity // Marks this class as a JPA entity.
@Table(name = "dummyTable")
public class DummyEntity {

    /**
     * The unique identifier for the entity.
     */
    @Id
    private UUID id;

    /**
     * The name of the entity. This field is required (non-null).
     */
    @NonNull
    private String name;
}