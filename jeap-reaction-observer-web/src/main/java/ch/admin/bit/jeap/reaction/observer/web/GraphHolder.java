package ch.admin.bit.jeap.reaction.observer.web;


import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import org.springframework.stereotype.Component;

@Component
public class GraphHolder {
    private Graph graph;

    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }
}
