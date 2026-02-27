package ch.admin.bit.jeap.reaction.observer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JpaInterfaceRepository extends JpaRepository<InterfaceEntity, Long> {

    Optional<InterfaceEntity> findByTypeAndFqn(String type, String fqn);

    /**
     * Inserts a new interface row using the next sequence value.
     * ON CONFLICT DO NOTHING ensures no exception is thrown if the (type, fqn) pair already exists.
     * A subsequent {@link #findByTypeAndFqn} must be used to retrieve the (possibly pre-existing) entity.
     */
    @Modifying
    @Query(value = "INSERT INTO interface (id, type, fqn) VALUES (nextval('interface_sequence'), :type, :fqn) ON CONFLICT DO NOTHING", nativeQuery = true)
    void insertIfNotExists(@Param("type") String type, @Param("fqn") String fqn);
}
