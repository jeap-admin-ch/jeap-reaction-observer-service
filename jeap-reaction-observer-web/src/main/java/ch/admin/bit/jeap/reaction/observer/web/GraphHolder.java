package ch.admin.bit.jeap.reaction.observer.web;


import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The graph this instance answers from, and the fingerprints of its subgraphs.
 * <p>
 * <b>Per JVM, not shared.</b> Every instance refreshes its own - see
 * {@code ScheduledTasksService#scheduledRefreshReactionGraph}, which is deliberately not locked.
 */
@Component
@RequiredArgsConstructor
public class GraphHolder {

    private final GraphSnapshotFactory snapshotFactory;

    /**
     * Volatile because it is written by the scheduler's thread and read by every request thread. The snapshot
     * itself is immutable, so a reader sees one whole graph or the previous one, never a mixture.
     */
    private volatile GraphSnapshot snapshot = GraphSnapshot.empty();

    public Graph getGraph() {
        return snapshot.graph();
    }

    /** Replaces the graph, and with it every fingerprint derived from it. */
    public void setGraph(Graph graph) {
        this.snapshot = snapshotFactory.of(graph);
    }

    public GraphSnapshot getSnapshot() {
        return snapshot;
    }
}
