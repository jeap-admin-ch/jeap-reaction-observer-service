package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.InterfaceNodeFactory;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionObserverMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

    /**
     * The metrics go to the application's registry; a registry of its own only stands in where there is none, e.g.
     * in a test that starts the persistence alone.
     */
    @Bean
    @ConditionalOnMissingBean
    ReactionObserverMetrics reactionObserverMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
        return new ReactionObserverMetrics(meterRegistry.getIfAvailable(SimpleMeterRegistry::new));
    }

    @Bean
    @ConditionalOnMissingBean
    InterfaceNodeFactory interfaceNodeFactory(ReactionObserverMetrics reactionObserverMetrics) {
        return new InterfaceNodeFactory(reactionObserverMetrics);
    }

    @Bean
    ReactionRepositoryImpl identifiedReactionRepository(JpaReactionRepository jpaReactionRepository, JpaObservationPropertiesRepository propertiesRepository, JpaInterfaceRepository jpaInterfaceRepository) {
        return new ReactionRepositoryImpl(jpaReactionRepository, propertiesRepository, jpaInterfaceRepository);
    }

    @Bean
    ObservedReactionRepositoryImpl observedReactionRepository(JpaObservedReactionRepository jpaObservedReactionRepository, JpaReactionRepository jpaReactionRepository, ReactionObserverMetrics reactionObserverMetrics) {
        return new ObservedReactionRepositoryImpl(jpaObservedReactionRepository, jpaReactionRepository, reactionObserverMetrics);
    }

    @Bean
    ObservedReactionsAggregatedRepositoryImpl observedReactionsAggregatedRepository(JdbcTemplate jdbcTemplate) {
        return new ObservedReactionsAggregatedRepositoryImpl(jdbcTemplate);
    }

    @Bean
    ReactionGraphRepositoryImpl reactionGraphRepository(JpaReactionRepository jpaReactionRepository, InterfaceNodeFactory interfaceNodeFactory) {
        return new ReactionGraphRepositoryImpl(jpaReactionRepository, interfaceNodeFactory);
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
