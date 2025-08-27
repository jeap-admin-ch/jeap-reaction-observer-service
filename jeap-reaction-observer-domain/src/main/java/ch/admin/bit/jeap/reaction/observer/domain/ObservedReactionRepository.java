package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.ObservedReaction;

import java.time.ZonedDateTime;
import java.util.List;

public interface ObservedReactionRepository {

    void saveAll(String idempotenceId, List<ObservedReaction> observedReactions);

    void deleteByTimeframeStartBefore(ZonedDateTime startOfDay);
}
