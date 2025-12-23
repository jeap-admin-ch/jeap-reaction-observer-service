package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ComponentRepository;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
class ComponentRepositoryImpl implements ComponentRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<String> getComponentNames() {
        return jdbcTemplate.queryForList("select distinct component from reaction", String.class);
    }
}
