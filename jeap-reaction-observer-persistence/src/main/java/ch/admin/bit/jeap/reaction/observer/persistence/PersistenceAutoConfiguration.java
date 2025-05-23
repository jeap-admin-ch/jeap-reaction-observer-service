package ch.admin.bit.jeap.reaction.observer.persistence;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@AutoConfiguration
@EnableTransactionManagement
@EnableJpaRepositories
@EntityScan
class PersistenceAutoConfiguration {

    @Bean
    ReactionRepositoryImpl identifiedReactionRepository(JpaReactionRepository jpaReactionRepository, JpaObservationPropertiesRepository propertiesRepository) {
        return new ReactionRepositoryImpl(jpaReactionRepository, propertiesRepository);
    }

    @Bean
    ObservedReactionRepositoryImpl observedReactionRepository(JpaObservedReactionRepository jpaObservedReactionRepository, JpaReactionRepository jpaReactionRepository) {
        return new ObservedReactionRepositoryImpl(jpaObservedReactionRepository, jpaReactionRepository);
    }

    @Bean
    ObservedReactionsAggregatedRepositoryImpl observedReactionsAggregatedRepository(JdbcTemplate jdbcTemplate) {
        return new ObservedReactionsAggregatedRepositoryImpl(jdbcTemplate);
    }
}
