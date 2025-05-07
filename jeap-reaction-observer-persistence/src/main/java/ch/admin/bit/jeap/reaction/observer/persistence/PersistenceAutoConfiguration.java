package ch.admin.bit.jeap.reaction.observer.persistence;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@AutoConfiguration
@EnableTransactionManagement
@EnableJpaRepositories
@EntityScan
class PersistenceAutoConfiguration {

    @Bean
    ReactionRepositoryImpl identifiedReactionRepository(JpaReactionRepository repository, JpaObservationPropertiesRepository propertiesRepository) {
        return new ReactionRepositoryImpl(repository, propertiesRepository);
    }
}
