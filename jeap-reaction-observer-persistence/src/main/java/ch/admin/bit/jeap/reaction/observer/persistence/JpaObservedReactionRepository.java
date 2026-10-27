package ch.admin.bit.jeap.reaction.observer.persistence;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Repository
interface JpaObservedReactionRepository extends CrudRepository<ObservedReactionEntity, Long> {

    boolean existsByIdempotenceId(String idempotenceId);

    @Transactional
    @Modifying
    @Query(value = """
            DELETE FROM observed_reaction
            WHERE id IN (SELECT id FROM observed_reaction WHERE observation_date < :date LIMIT :batchSize)
            """, nativeQuery = true)
    int deleteBatchByObservationDateBefore(@Param("date") LocalDate date, @Param("batchSize") int batchSize);
}
