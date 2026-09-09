package ch.admin.bit.jeap.reaction.observer.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphSnapshotTest {

    @Test
    void required_isTheSnapshotItself() {
        GraphSnapshot snapshot = GraphSnapshot.empty();

        assertThat(GraphSnapshot.required(snapshot)).isSameAs(snapshot);
    }

    /**
     * The one way a resource can find no snapshot is a test that mocks {@link GraphHolder} - so the message
     * has to name both ways out, or a downstream test reads an unexplained {@code NullPointerException}
     * instead.
     */
    @Test
    void required_withoutASnapshot_saysWhatAMockedGraphHolderHasToDo() {
        assertThatThrownBy(() -> GraphSnapshot.required(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GraphHolder")
                .hasMessageContaining("stub getSnapshot()")
                .hasMessageContaining("setGraph(graph)");
    }
}
