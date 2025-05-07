package ch.admin.bit.jeap.reaction.observer.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaReactionRepository extends CrudRepository<ReactionEntity, Long> {

    boolean existsByComponentAndReactionId(String component, String reactionId);

    Optional<ReactionEntity> findByComponentAndReactionId(String component, String reactionId);
}
