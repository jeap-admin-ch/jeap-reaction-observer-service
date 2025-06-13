package ch.admin.bit.jeap.reaction.observer.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.stream.Stream;

@Repository
interface JpaObservationPropertiesRepository extends CrudRepository<ObservationProperty, Long> {

    Stream<ObservationProperty> findByReactionTriggerFk(Long reactionId);

    Stream<ObservationProperty> findByActionFk(Long reactionId);
}
