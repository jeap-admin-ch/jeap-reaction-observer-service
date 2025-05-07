package ch.admin.bit.jeap.reaction.observer.domain;

import java.util.List;

public interface ObservedReactionRepository {

    void saveAll(String idempotenceId, List<ObservedReaction> observedReactions);
}
