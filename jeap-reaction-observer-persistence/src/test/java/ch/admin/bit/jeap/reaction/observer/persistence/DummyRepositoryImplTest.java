package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.DummyEntity;
import ch.admin.bit.jeap.reaction.observer.domain.DummyRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Slf4j
class DummyRepositoryImplTest {

    @Autowired
    private DummyRepository dummyRepository;

    @Test
    void findAll() {
        List<DummyEntity> emptyList = dummyRepository.findAll();
        assertThat(emptyList).isEmpty();
    }

}
