package ch.admin.bit.jeap.reaction.observer.persistence;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
interface JpaReactionRepository extends CrudRepository<ReactionEntity, Long> {

    boolean existsByComponentAndReactionId(String component, String reactionId);

    @Cacheable(value = "reactionByComponentAndReactionId")
    Optional<ReactionEntity> findByComponentAndReactionId(String component, String reactionId);

    @Cacheable(value = "reactionIdByComponentAndReactionId")
    @Query("select id from ReactionEntity where component = :component and reactionId = :reactionId")
    Optional<Long> findIdByComponentAndReactionId(String component, String reactionId);
}
