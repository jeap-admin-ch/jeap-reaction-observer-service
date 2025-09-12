package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;

public interface ReactionGraphRepository {
    Graph buildFullGraph();
}

