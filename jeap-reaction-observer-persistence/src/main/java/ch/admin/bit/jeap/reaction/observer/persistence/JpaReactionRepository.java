package ch.admin.bit.jeap.reaction.observer.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface JpaReactionRepository extends CrudRepository<ReactionEntity, Long> {

    boolean existsByComponentAndReactionId(String component, String reactionId);


    @EntityGraph(value = "Reaction.withActions", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT r FROM ReactionEntity r")
    List<ReactionEntity> findAllWithActions();


    Optional<ReactionEntity> findByComponentAndReactionId(String component, String reactionId);

    @Query("select id from ReactionEntity where component = :component and reactionId = :reactionId")
    Optional<Long> findIdByComponentAndReactionId(String component, String reactionId);
}
