package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Slf4j
class ObservedReactionRepositoryImplTest {

    @Autowired
    private ObservedReactionRepository observedReactionRepository;

    @Autowired
    private JpaObservedReactionRepository jpaObservedReactionRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Test
    void saveAll_noDuplicates() {
        // given: a reaction and an observation of the reaction
        String reactionId = "reactionId123";
        String component = "component123";
        String idempotenceId = "idempotence123";
        Reaction reaction = new Reaction(component, reactionId,
                new Observation("triggerType", "triggerFqn", Map.of()),
                new Observation("actionType", "actionFqn", Map.of()),
                ZonedDateTime.now());
        reactionRepository.save(reaction);
        int count = 5;
        ObservedReaction observedReaction = new ObservedReaction(component, reactionId,
                new Timeframe(ZonedDateTime.now(), ZonedDateTime.now().plusHours(1)), count);

        // when: the observed reaction is saved (twice)
        observedReactionRepository.saveAll(idempotenceId, List.of(observedReaction));
        observedReactionRepository.saveAll(idempotenceId, List.of(observedReaction));

        // then: the observed reaction is stored once in the repository and can be retrieved
        var foundObservedReactions = StreamSupport.stream(jpaObservedReactionRepository.findAll().spliterator(), false)
                .filter(entity -> entity.getIdempotenceId().equals(idempotenceId))
                .map(entity -> entity.toDomainObject(reaction))
                .toList();

        assertThat(foundObservedReactions)
                .containsExactly(observedReaction);
    }

}
