package ch.admin.bit.jeap.reaction.observer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaInterfaceRepository extends JpaRepository<InterfaceEntity, Long> {

    Optional<InterfaceEntity> findByTypeAndFqn(String type, String fqn);
}
