package ch.admin.bit.jeap.reaction.observer.persistence;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@AutoConfiguration
@EnableTransactionManagement
@EnableJpaRepositories
@EntityScan
class PersistenceAutoConfiguration {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }

    @Bean
    ReactionRepositoryImpl identifiedReactionRepository(JpaReactionRepository jpaReactionRepository, JpaObservationPropertiesRepository propertiesRepository, JpaInterfaceRepository jpaInterfaceRepository) {
        return new ReactionRepositoryImpl(jpaReactionRepository, propertiesRepository, jpaInterfaceRepository);
    }

    @Bean
    ObservedReactionRepositoryImpl observedReactionRepository(JpaObservedReactionRepository jpaObservedReactionRepository, JpaReactionRepository jpaReactionRepository) {
        return new ObservedReactionRepositoryImpl(jpaObservedReactionRepository, jpaReactionRepository);
    }

    @Bean
    ObservedReactionsAggregatedRepositoryImpl observedReactionsAggregatedRepository(JdbcTemplate jdbcTemplate) {
        return new ObservedReactionsAggregatedRepositoryImpl(jdbcTemplate);
    }

    @Bean
    ReactionGraphRepositoryImpl reactionGraphRepository(JpaReactionRepository jpaReactionRepository) {
        return new ReactionGraphRepositoryImpl(jpaReactionRepository);
    }

    @Bean
    SystemRepositoryImpl systemRepository(JdbcTemplate jdbcTemplate) {
        return new SystemRepositoryImpl(jdbcTemplate);
    }

    @Bean
    ComponentRepositoryImpl componentRepository(JdbcTemplate jdbcTemplate) {
        return new ComponentRepositoryImpl(jdbcTemplate);
    }
}
