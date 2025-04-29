package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.DummyEntity;
import ch.admin.bit.jeap.reaction.observer.domain.DummyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementation of the DummyRepository interface.
 * This class acts as a bridge between the domain layer and the persistence layer.
 * It uses JpaDummyRepository to interact with the database.
 */
@Component
@RequiredArgsConstructor
public class DummyRepositoryImpl implements DummyRepository {

    // The JPA repository used for performing CRUD operations on DummyEntity.
    private final JpaDummyRepository jpaDummyRepository;

    /**
     * Retrieves all DummyEntity records from the database.
     *
     * @return a list of DummyEntity objects.
     */
    @Override
    public List<DummyEntity> findAll() {
        return (List<DummyEntity>) jpaDummyRepository.findAll();
    }
}
