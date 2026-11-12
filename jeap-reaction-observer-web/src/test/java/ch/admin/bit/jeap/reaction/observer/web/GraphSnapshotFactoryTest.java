package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Action;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Edge;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.GenericInterface;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.HttpOperation;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Node;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.SemanticType;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Trigger;
import ch.admin.bit.jeap.reaction.observer.web.api.EtagSupport;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphSnapshotFactoryTest {

    private static final String CONTEXT_PATH = "/jeap-reaction-observer";

    private final GraphFingerprintCalculator calculator =
            new GraphFingerprintCalculator(JsonMapper.builder().build());

    private final GraphSnapshotFactory factory = new GraphSnapshotFactory(new GraphExtractor(), calculator,
            new EtagSupport(JsonMapper.builder().build()), CONTEXT_PATH);

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
     * The v1 serves the messages only: a graph that also has other interfaces is fingerprinted and indexed as its
     * view of messages, and keeps the full graph as it is.
     */
    @Test
    void of_derivesEverythingFromTheMessageView_andKeepsTheFullGraph() {
        Graph messagesOnly = aGraph();
        Reaction restOnly = Reaction.builder().id(900).component("stock-service").system("stock").median(1).build();
        HttpOperation putStock = new HttpOperation(901, "stock-service", "PUT", "/api/stock/{id}");
        List<Node> nodes = new ArrayList<>(messagesOnly.nodes());
        nodes.addAll(List.of(restOnly, putStock));
        List<Edge> edges = new ArrayList<>(messagesOnly.edges());
        edges.add(new Trigger(putStock, restOnly, 1));
        Graph withHttp = new Graph(nodes, edges);

        GraphSnapshot snapshot = factory.of(withHttp);

        assertThat(snapshot.fullGraph()).isSameAs(withHttp);
        assertThat(snapshot.graph().nodes()).doesNotContain(restOnly, putStock);
        assertThat(snapshot.graphFingerprint()).isEqualTo(factory.of(messagesOnly).graphFingerprint());
        assertThat(snapshot.components()).extracting(GraphSnapshot.ComponentEntry::name)
                .containsExactly("orders-intake");
        assertThat(snapshot.fingerprintOfSystem("stock")).isNull();
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

    // --- The index payloads, built once with the graph ----------------------------------------------------

    /**
     * The path an index publishes has to carry the context path the service runs under, or a consumer cannot
     * use it as it stands - which is the whole point of publishing it.
     */
    @Test
    void of_theIndexPaths_carryTheContextPath() {
        GraphSnapshot snapshot = factory.of(aGraph());

        assertThat(asText(snapshot.systemIndex()))
                .contains("\"path\":\"" + CONTEXT_PATH + "/api/graphs/systems/orders\"");
        assertThat(asText(snapshot.componentIndex()))
                .contains("\"path\":\"" + CONTEXT_PATH + "/api/graphs/components/orders-intake\"");
        assertThat(asText(snapshot.messageIndex()))
                .contains("\"path\":\"" + CONTEXT_PATH
                          + "/api/graphs/messages/OrdersPaymentAcceptedEvent\"");
    }

    @Test
    void of_withoutAContextPath_theIndexPathsStartAtTheApi() {
        GraphSnapshotFactory withoutContextPath = new GraphSnapshotFactory(new GraphExtractor(), calculator,
                new EtagSupport(JsonMapper.builder().build()), "");

        assertThat(asText(withoutContextPath.of(aGraph()).systemIndex()))
                .contains("\"path\":\"/api/graphs/systems/orders\"");
    }

    @Test
    void of_tagsEachIndexOverItsOwnBytes() {
        GraphSnapshot snapshot = factory.of(aGraph());

        assertThat(snapshot.systemIndex().etag()).startsWith("\"sha256:");
        assertThat(snapshot.systemIndex().etag())
                .describedAs("two indexes of different content have different tags")
                .isNotEqualTo(snapshot.componentIndex().etag());
    }

    /** An index entry has to carry the tag its graph resource answers with, or comparing them is pointless. */
    @Test
    void of_theIndexEntryTag_isTheTagOfTheGraphResource() {
        GraphSnapshot snapshot = factory.of(aGraph());

        String fingerprintOfTheSystem = snapshot.fingerprintOfSystem("orders");

        assertThat(asText(snapshot.systemIndex()))
                .contains("\"etag\":\"\\\"sha256:" + fingerprintOfTheSystem + "\\\"\"");
    }

    // --- The API v2, over the full graph ---------------------------------------------------------------------

    /** What the v1 hides is what the v2 is for: a component whose reactions have no message is indexed there. */
    @Test
    void of_theV2_coversTheFullGraph_whereTheV1CoversTheMessagesOnly() {
        GraphSnapshot snapshot = factory.of(GraphV2TestGraph.graph());

        assertThat(asText(snapshot.v2().componentIndex()))
                .contains("\"name\":\"order-service\"", "\"name\":\"partner-service\"");
        assertThat(snapshot.v2().fingerprintOfSystem("SHOP")).isNotBlank();
        assertThat(snapshot.v2().fingerprintOfComponent("partner-service")).isNotBlank();
        assertThat(snapshot.v2().fingerprintsByInterface()).containsOnlyKeys(11L, 12L, 13L, 14L, 15L, 16L);
    }

    @Test
    void of_theV2_hasFingerprintsOfItsOwn() {
        Graph messagesOnly = aGraph();
        Reaction restOnly = Reaction.builder().id(900).component("stock-service").system("stock").median(1).build();
        HttpOperation putStock = new HttpOperation(901, "stock-service", "PUT", "/api/stock/{id}");
        List<Node> nodes = new ArrayList<>(messagesOnly.nodes());
        nodes.addAll(List.of(restOnly, putStock));
        List<Edge> edges = new ArrayList<>(messagesOnly.edges());
        edges.add(new Trigger(putStock, restOnly, 1));

        GraphSnapshot before = factory.of(messagesOnly);
        GraphSnapshot after = factory.of(new Graph(nodes, edges));

        // The same graph has another fingerprint in each version, as each fingerprints its own representation
        assertThat(before.v2().graphFingerprint()).isNotBlank().isNotEqualTo(before.graphFingerprint());
        assertThat(before.v2().fingerprintOfSystem("orders")).isNotEqualTo(before.fingerprintOfSystem("orders"));
        // A reaction without a message changes the v2 and leaves the v1 as it is
        assertThat(after.graphFingerprint()).isEqualTo(before.graphFingerprint());
        assertThat(after.v2().graphFingerprint()).isNotEqualTo(before.v2().graphFingerprint());
        assertThat(after.v2().fingerprintOfSystem("stock")).isNotBlank();
        // And what did not change keeps its fingerprint
        assertThat(after.v2().fingerprintOfSystem("orders")).isEqualTo(before.v2().fingerprintOfSystem("orders"));
    }

    @Test
    void of_theV2InterfaceIndex_listsEveryInterfaceByTypeAndFqn_withTheTagAndPathOfItsGraph() {
        GraphSnapshotV2 v2 = factory.of(GraphV2TestGraph.graph()).v2();

        String index = asText(v2.interfaceIndex(null));
        assertThat(index).containsSubsequence(
                "\"fqn\":\"CreditReserved\"", "\"fqn\":\"OrderChecked/eu\"", "\"fqn\":\"OrderSubmitted\"",
                "\"fqn\":\"order-service POST /api/orders\"", "\"fqn\":\"partner-service POST /api/credits\"",
                "\"fqn\":\"nightly-job\"");
        assertThat(index).contains("{\"id\":\"I14\",\"interfaceType\":\"http_request\","
                                   + "\"fqn\":\"partner-service POST /api/credits\","
                                   + "\"etag\":\"\\\"sha256:" + v2.fingerprintOfInterface(14) + "\\\"\","
                                   + "\"path\":\"" + CONTEXT_PATH + "/api/v2/graphs/interfaces/I14\"}");
    }

    @Test
    void of_theV2InterfaceIndex_canBeAskedForOneType() {
        GraphSnapshotV2 v2 = factory.of(GraphV2TestGraph.graph()).v2();

        assertThat(asText(v2.interfaceIndex("http_request")))
                .contains("\"id\":\"I14\"", "\"id\":\"I15\"")
                .doesNotContain("\"interfaceType\":\"event\"", "\"interfaceType\":\"scheduled\"");
        assertThat(asText(v2.interfaceIndex("scheduled"))).contains("\"id\":\"I16\"").doesNotContain("I14");
        // A type the graph has no interface of is an empty index with a tag - and the type is matched exactly
        assertThat(asText(v2.interfaceIndex("unknown"))).isEqualTo("{\"entries\":[]}");
        assertThat(asText(v2.interfaceIndex("HTTP_REQUEST"))).isEqualTo("{\"entries\":[]}");
        assertThat(v2.interfaceIndex("unknown").etag()).startsWith("\"sha256:")
                .isNotEqualTo(v2.interfaceIndex("http_request").etag());
    }

    /**
     * A stored interface always has a type and an FQN, a graph built by other means may not. The snapshot is
     * what every endpoint answers from, the ones of the v1 included, so such an interface must not fail it.
     */
    @Test
    void of_anInterfaceWithoutTypeOrFqn_isIndexedFirst_andNotUnderAType() {
        Reaction reaction = Reaction.builder().id(1).component("some-service").system("some").build();
        GenericInterface withoutType = new GenericInterface(2, null, "something");
        GenericInterface withoutFqn = new GenericInterface(3, "scheduled", null);
        GenericInterface complete = new GenericInterface(4, "scheduled", "nightly-job");
        Graph graph = new Graph(List.of(reaction, withoutType, withoutFqn, complete),
                List.of(new Trigger(withoutType, reaction, 1), new Action(reaction, withoutFqn),
                        new Action(reaction, complete)));

        GraphSnapshotV2 v2 = factory.of(graph).v2();

        assertThat(asText(v2.interfaceIndex(null)))
                .containsSubsequence("\"id\":\"I2\"", "\"id\":\"I3\"", "\"id\":\"I4\"");
        assertThat(asText(v2.interfaceIndex("scheduled")))
                .containsSubsequence("\"id\":\"I3\"", "\"id\":\"I4\"")
                .doesNotContain("\"id\":\"I2\"");
        assertThat(v2.fingerprintOfInterface(2)).isNotBlank();
    }

    @Test
    void of_theV2IndexPaths_carryTheContextPathAndTheVersion() {
        GraphSnapshotV2 v2 = factory.of(GraphV2TestGraph.graph()).v2();

        assertThat(asText(v2.systemIndex())).contains("\"path\":\"" + CONTEXT_PATH + "/api/v2/graphs/systems/shop\"");
        assertThat(asText(v2.componentIndex()))
                .contains("\"path\":\"" + CONTEXT_PATH + "/api/v2/graphs/components/order-service\"");
    }

    private static String asText(GraphSnapshot.IndexPayload payload) {
        return new String(payload.bytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
