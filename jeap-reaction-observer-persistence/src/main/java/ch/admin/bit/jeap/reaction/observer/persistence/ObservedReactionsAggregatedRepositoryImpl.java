package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedStatistics;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

class ObservedReactionsAggregatedRepositoryImpl implements ObservedReactionsAggregatedRepository {

    private final JpaObservedReactionsAggregatedRepository jpaObservedReactionsAggregatedRepository;

    public ObservedReactionsAggregatedRepositoryImpl(JpaObservedReactionsAggregatedRepository jpaObservedReactionsAggregatedRepository) {
        this.jpaObservedReactionsAggregatedRepository = jpaObservedReactionsAggregatedRepository;
    }

    @Transactional
    @Override
    public void aggregateObservedReactionsForDay(LocalDate date) {
        jpaObservedReactionsAggregatedRepository.aggregateForDate(date);
    }

    @Transactional
    @Override
    public List<ObservedReactionsAggregatedStatistics> getStatistics(String component, LocalDate fromDate) {
        return jpaObservedReactionsAggregatedRepository.getStatistics(component, fromDate).stream().map(row -> {
            String comp = (String) row[0];
            String triggerType = (String) row[1];
            String triggerFqn = (String) row[2];
            String actionType = (String) row[3];
            String actionFqn = (String) row[4];
            int count = ((Number) row[5]).intValue();
            float median = ((Number) row[6]).floatValue();
            float percentage = ((Number) row[7]).floatValue();

            return new ObservedReactionsAggregatedStatistics(comp, triggerType, triggerFqn, actionType, actionFqn, count, median, percentage);
        }).toList();
    }

    @Override
    public void deleteAggregatedDataOlderThan(LocalDate date) {
        jpaObservedReactionsAggregatedRepository.deleteByDateBefore(date);
    }

}
