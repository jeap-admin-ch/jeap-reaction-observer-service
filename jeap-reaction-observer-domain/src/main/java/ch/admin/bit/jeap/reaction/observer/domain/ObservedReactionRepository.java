package ch.admin.bit.jeap.reaction.observer.domain;

import java.time.ZonedDateTime;
import java.util.List;

public interface ObservedReactionRepository {

    void saveAll(String idempotenceId, List<ObservedReaction> observedReactions);

    void deleteByTimeframeStartBefore(ZonedDateTime startOfDay);
}
