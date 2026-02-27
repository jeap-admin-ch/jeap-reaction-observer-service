package ch.admin.bit.jeap.reaction.observer.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
class JpaInterfaceRepositoryTest {

    @Autowired
    private JpaInterfaceRepository jpaInterfaceRepository;

    @Test
    void insertIfNotExists_insertsNewRow() {
        // when
        jpaInterfaceRepository.insertIfNotExists("EventType", "ch.example.EventType");

        // then: row is present
        Optional<InterfaceEntity> result = jpaInterfaceRepository.findByTypeAndFqn("EventType", "ch.example.EventType");
        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo("EventType");
        assertThat(result.get().getFqn()).isEqualTo("ch.example.EventType");
        assertThat(result.get().getId()).isNotNull();
    }

    @Test
    void insertIfNotExists_doesNotInsertDuplicate() {
        // given: row already exists
        jpaInterfaceRepository.insertIfNotExists("DupType", "ch.example.DupType");
        InterfaceEntity first = jpaInterfaceRepository.findByTypeAndFqn("DupType", "ch.example.DupType").orElseThrow();

        // when: same (type, fqn) inserted again — ON CONFLICT DO NOTHING
        jpaInterfaceRepository.insertIfNotExists("DupType", "ch.example.DupType");

        // then: still exactly one row, same id
        List<InterfaceEntity> all = jpaInterfaceRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getId()).isEqualTo(first.getId());
    }

    @Test
    void insertIfNotExists_doesNotThrow_whenRowAlreadyExists() {
        // given: row already exists (simulates concurrent insert from another transaction)
        jpaInterfaceRepository.insertIfNotExists("ConcurrentType", "ch.example.ConcurrentType");

        // when / then: second call must not throw any exception
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                jpaInterfaceRepository.insertIfNotExists("ConcurrentType", "ch.example.ConcurrentType")
        );
    }

    @Test
    void insertIfNotExists_differentTypeOrFqn_insertsBothRows() {
        // when
        jpaInterfaceRepository.insertIfNotExists("TypeA", "ch.example.A");
        jpaInterfaceRepository.insertIfNotExists("TypeB", "ch.example.A"); // same fqn, different type
        jpaInterfaceRepository.insertIfNotExists("TypeA", "ch.example.B"); // same type, different fqn

        // then: all three are distinct rows
        List<InterfaceEntity> all = jpaInterfaceRepository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all).extracting(InterfaceEntity::getType)
                .containsExactlyInAnyOrder("TypeA", "TypeB", "TypeA");
    }
}

