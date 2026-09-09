package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Action;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.SemanticType;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Trigger;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphSnapshotFactoryTest {

    private final GraphSnapshotFactory factory =
            new GraphSnapshotFactory(new GraphExtractor(), new GraphFingerprintCalculator(JsonMapper.builder().build()));

    @Test
    void of_indexesEverySystemComponentAndMessageTypeThatHasAReaction() {
        GraphSnapshot snapshot = factory.of(aGraph());

        assertThat(snapshot.systems()).extracting(GraphSnapshot.SystemEntry::name)
                .containsExactly("orders");
        assertThat(snapshot.components()).extracting(GraphSnapshot.ComponentEntry::name)
                .containsExactly("orders-intake");
        assertThat(snapshot.messageTypes()).extracting(GraphSnapshot.MessageTypeEntry::messageType)
                .containsExactly("OrdersPaymentAcceptedEvent", "OrdersPaymentSettledEvent");
    }

    @Test
    void of_namesTheSystemOfEveryComponent() {
        GraphSnapshot snapshot = factory.of(aGraph());

        assertThat(snapshot.components()).singleElement()
                .satisfies(component -> assertThat(component.system()).isEqualTo("orders"));
    }

    @Test
    void of_fingerprintsEverySubgraph() {
        GraphSnapshot snapshot = factory.of(aGraph());

        assertThat(snapshot.graphFingerprint()).isNotBlank();
        assertThat(snapshot.systems()).allSatisfy(entry ->
                assertThat(entry.fingerprint()).isNotBlank());
        assertThat(snapshot.components()).allSatisfy(entry ->
                assertThat(entry.fingerprint()).isNotBlank());
        assertThat(snapshot.messageTypes()).allSatisfy(entry ->
                assertThat(entry.fingerprint()).isNotBlank());
    }

    /**
     * The fingerprint of a subgraph in the index has to be the one the graph resource answers with, or a
     * consumer could not compare the two without fetching.
     */
    @Test
    void of_fingerprintsASystemAsTheResourceDoes() {
        Graph graph = aGraph();
        GraphSnapshot snapshot = factory.of(graph);
        GraphFingerprintCalculator calculator = new GraphFingerprintCalculator(JsonMapper.builder().build());

        String asTheResourceWouldAnswer = calculator.calculate(ch.admin.bit.jeap.reaction.observer.web.service
                .GraphDtoMapper.map(new GraphExtractor().getSystemRelatedGraph(graph, "orders")));

        assertThat(snapshot.fingerprintOfSystem("orders")).isEqualTo(asTheResourceWouldAnswer);
    }

    @Test
    void fingerprintOfSystem_ignoresCase_asTheExtractorDoes() {
        GraphSnapshot snapshot = factory.of(aGraph());

        assertThat(snapshot.fingerprintOfSystem("ORDERS"))
                .isEqualTo(snapshot.fingerprintOfSystem("orders"));
    }

    @Test
    void fingerprintOfComponent_matchesExactly_asTheExtractorDoes() {
        GraphSnapshot snapshot = factory.of(aGraph());

        assertThat(snapshot.fingerprintOfComponent("orders-intake")).isNotBlank();
        assertThat(snapshot.fingerprintOfComponent("ORDERS-INTAKE")).isNull();
    }

    @Test
    void fingerprintOf_isNullForSomethingTheGraphDoesNotHave() {
        GraphSnapshot snapshot = factory.of(aGraph());

        assertThat(snapshot.fingerprintOfSystem("no-such-system")).isNull();
        assertThat(snapshot.fingerprintOfComponent("no-such-component")).isNull();
        assertThat(snapshot.fingerprintOfMessageType("NoSuchEvent")).isNull();
    }

    /**
     * A message type with two variants is one entry with both keys, because the resource answers both at
     * once - and its tag covers them together.
     */
    @Test
    void of_listsEveryVariantOfAMessageTypeUnderOneEntry() {
        Message withoutVariant = Message.builder().id(1).messageType("OrdersPaymentAcceptedEvent")
                .semantic(SemanticType.EVENT).build();
        Message withVariant = Message.builder().id(2).messageType("OrdersPaymentAcceptedEvent")
                .variant("express").semantic(SemanticType.EVENT).build();
        Reaction reaction = Reaction.builder().id(3).component("orders-intake").system("orders").build();
        Graph graph = new Graph(List.of(withoutVariant, withVariant, reaction),
                List.of(Trigger.builder().source(withoutVariant).target(reaction).build(),
                        Trigger.builder().source(withVariant).target(reaction).build()));

        GraphSnapshot snapshot = factory.of(graph);

        assertThat(snapshot.messageTypes()).singleElement().satisfies(entry -> {
            assertThat(entry.variantKeys()).containsExactly("OrdersPaymentAcceptedEvent",
                    "OrdersPaymentAcceptedEvent/express");
            assertThat(entry.fingerprint()).isNotBlank();
        });
    }

    @Test
    void of_anEmptyGraph_indexesNothing() {
        GraphSnapshot snapshot = factory.of(new Graph(List.of(), List.of()));

        assertThat(snapshot.systems()).isEmpty();
        assertThat(snapshot.components()).isEmpty();
        assertThat(snapshot.messageTypes()).isEmpty();
        assertThat(snapshot.graphFingerprint()).isNotBlank();
    }

    @Test
    void of_null_isTheEmptySnapshot() {
        GraphSnapshot snapshot = factory.of(null);

        assertThat(snapshot.graph().nodes()).isEmpty();
        assertThat(snapshot.systems()).isEmpty();
    }

    /** A reaction whose system is unknown is still a component with a graph. */
    @Test
    void of_aReactionWithoutASystem_isIndexedAsAComponent() {
        Reaction reaction = Reaction.builder().id(1).component("orphan-service").build();
        Message message = Message.builder().id(2).messageType("SomeEvent").semantic(SemanticType.EVENT).build();
        Graph graph = new Graph(List.of(reaction, message),
                List.of(Trigger.builder().source(message).target(reaction).build()));

        GraphSnapshot snapshot = factory.of(graph);

        assertThat(snapshot.systems()).isEmpty();
        assertThat(snapshot.components()).singleElement().satisfies(component -> {
            assertThat(component.name()).isEqualTo("orphan-service");
            assertThat(component.system()).isNull();
        });
    }

    /**
     * One system, one component, two message types - one triggering the reaction and one it produces.
     */
    private static Graph aGraph() {
        Message trigger = Message.builder().id(1).messageType("OrdersPaymentAcceptedEvent")
                .semantic(SemanticType.EVENT).build();
        Message action = Message.builder().id(2).messageType("OrdersPaymentSettledEvent")
                .semantic(SemanticType.EVENT).build();
        Reaction reaction = Reaction.builder().id(3).component("orders-intake").system("orders").build();
        return new Graph(List.of(trigger, action, reaction),
                List.of(Trigger.builder().source(trigger).target(reaction).median(5).build(),
                        Action.builder().source(reaction).target(action).build()));
    }
}
