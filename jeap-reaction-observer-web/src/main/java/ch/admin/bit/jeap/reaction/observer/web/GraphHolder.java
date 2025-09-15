package ch.admin.bit.jeap.reaction.observer.web;


import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class GraphHolder {
    private Graph graph;
}
