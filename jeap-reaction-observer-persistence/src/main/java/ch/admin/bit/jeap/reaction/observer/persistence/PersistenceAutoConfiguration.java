package ch.admin.bit.jeap.reaction.observer.persistence;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.concurrent.TimeUnit;

@AutoConfiguration
@EnableTransactionManagement
@EnableJpaRepositories
@EntityScan
@EnableCaching
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
    ObservedReactionsAggregatedRepositoryImpl observedReactionsAggregatedRepository(JpaObservedReactionsAggregatedRepository jpaObservedReactionsAggregatedRepository) {
        return new ObservedReactionsAggregatedRepositoryImpl(jpaObservedReactionsAggregatedRepository);
    }

    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(24, TimeUnit.HOURS);
    }

    @Bean
    public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeine);
        return cacheManager;
    }
}
