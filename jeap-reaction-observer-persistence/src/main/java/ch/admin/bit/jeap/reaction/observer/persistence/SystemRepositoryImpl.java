package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.SystemRepository;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
class SystemRepositoryImpl implements SystemRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<String> getSystemNames() {
        return jdbcTemplate.queryForList("select distinct system from reaction", String.class);
    }
}
