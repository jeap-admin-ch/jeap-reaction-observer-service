package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.DummyEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

/**
 * Implementation of the DummyRepository interface.
 * This class acts as a bridge between the domain layer and the persistence layer.
 * It uses JpaDummyRepository to interact with the database.
 */
public interface JpaDummyRepository extends CrudRepository<DummyEntity, UUID> {

}
