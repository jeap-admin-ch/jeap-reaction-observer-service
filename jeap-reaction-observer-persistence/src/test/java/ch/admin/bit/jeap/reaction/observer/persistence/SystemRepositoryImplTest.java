package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ReactionRepository;
import ch.admin.bit.jeap.reaction.observer.domain.SystemRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.Observation;
import ch.admin.bit.jeap.reaction.observer.domain.models.Reaction;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Slf4j
class SystemRepositoryImplTest {

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private SystemRepository systemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void getNoSystems() {
        List<String> systemNames = systemRepository.getSystemNames();
        assertThat(systemNames).isEmpty();
    }

    @Test
    void getSingleSystem() {
        Observation trigger = new Observation("t1", "triggerType", "triggerFqn", Map.of());
        Observation action = new Observation("a1", "actionType", "actionFqn", Map.of());

        reactionRepository.save(new Reaction("system0", "component0", "reaction0", trigger, List.of(action), ZonedDateTime.now()));
        entityManager.flush();

        List<String> systemNames = systemRepository.getSystemNames();
        assertThat(systemNames).contains("system0");
    }

    @Test
    void getMultipleSystems() {
        Observation trigger = new Observation("t1", "triggerType", "triggerFqn", Map.of());
        Observation action = new Observation("a1", "actionType", "actionFqn", Map.of());

        reactionRepository.save(new Reaction("system0", "component0", "reaction0", trigger, List.of(action), ZonedDateTime.now()));
        reactionRepository.save(new Reaction("system0", "component0", "reaction1", trigger, List.of(action), ZonedDateTime.now()));
        reactionRepository.save(new Reaction("system1", "component1", "reaction0", trigger, List.of(action), ZonedDateTime.now()));
        reactionRepository.save(new Reaction("system2", "component2", "reaction0", trigger, List.of(action), ZonedDateTime.now()));
        entityManager.flush();

        List<String> systemNames = systemRepository.getSystemNames();
        assertThat(systemNames).containsExactlyInAnyOrder("system0", "system1", "system2");
    }

}
