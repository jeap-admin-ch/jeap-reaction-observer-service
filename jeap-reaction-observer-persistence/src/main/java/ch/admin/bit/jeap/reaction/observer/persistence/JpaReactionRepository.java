package ch.admin.bit.jeap.reaction.observer.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
interface JpaReactionRepository extends CrudRepository<ReactionEntity, Long> {

    /**
     * Records the reaction model version of an identification of a reaction.
     *
     * @return the number of updated reactions, 0 if the reaction is not known
     */
    @Modifying
    @Query("update ReactionEntity r set r.lastIdentifiedByModelVersion = :modelVersion " +
           "where r.component = :component and r.reactionId = :reactionId")
    int updateLastIdentifiedByModelVersion(String component, String reactionId, int modelVersion);

    @EntityGraph(value = "Reaction.withActions", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT r FROM ReactionEntity r")
    List<ReactionEntity> findAllWithActions();


    Optional<ReactionEntity> findByComponentAndReactionId(String component, String reactionId);

    @Query("select id from ReactionEntity where component = :component and reactionId = :reactionId")
    Optional<Long> findIdByComponentAndReactionId(String component, String reactionId);

    /**
     * The ids of those of the given reactions of a component that are known, with the reaction id they belong to.
     * A reaction id that is not in the result is not known.
     */
    @Query("select r.reactionId as reactionId, r.id as id from ReactionEntity r " +
           "where r.component = :component and r.reactionId in :reactionIds")
    List<ReactionFk> findFksByComponentAndReactionIdIn(String component, Collection<String> reactionIds);

    interface ReactionFk {
        String getReactionId();

        Long getId();
    }
}
