package ch.admin.bit.jeap.reaction.observer.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;

@Repository
interface JpaObservedReactionRepository extends CrudRepository<ObservedReactionEntity, Long> {

    boolean existsByIdempotenceId(String idempotenceId);

    void deleteByTimeframeStartBefore(ZonedDateTime startOfDay);
}
