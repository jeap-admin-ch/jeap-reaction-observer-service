package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageGraphFilterTest {

    private static final Message ORDER_CREATED = new Message(1, "OrderCreated", null, SemanticType.EVENT);
    private static final Message SHIP_ORDER = new Message(2, "ShipOrder", "express", SemanticType.COMMAND);
    private static final HttpOperation POST_ORDER = new HttpOperation(3, "order-service", "POST", "/api/orders");
    private static final HttpOperation PUT_STOCK = new HttpOperation(4, "stock-service", "PUT", "/api/stock/{id}");
    private static final GenericInterface NIGHTLY_JOB = new GenericInterface(5, "scheduled", "nightly-job");

    @Test
    void filter_aGraphOfMessagesOnly_isReturnedAsItIs() {
        Reaction reaction = reaction(10);
        Reaction withoutEdges = reaction(11);
        Graph graph = new Graph(List.of(ORDER_CREATED, reaction, SHIP_ORDER, withoutEdges), List.of(
                new Trigger(ORDER_CREATED, reaction, 5),
                new Action(reaction, SHIP_ORDER)));

        assertThat(MessageGraphFilter.filter(graph)).isSameAs(graph);
    }

    @Test
    void filter_removesOtherInterfacesAndReactionsLeftWithoutMessages_andKeepsMessageActionsOfOtherTriggers() {
        // REST-triggered, with a message action and a REST action
        Reaction restTriggered = reaction(10);
        // REST-triggered only: nothing of it is a message
        Reaction triggerOnlyRest = reaction(11);
        // triggered by an unknown type, with a message action
        Reaction scheduled = reaction(12);
        // a reaction of messages only
        Reaction messagesOnly = reaction(13);
        // a reaction that has no edges at all, as it had before
        Reaction withoutEdges = reaction(14);

        Graph graph = new Graph(
                List.of(restTriggered, triggerOnlyRest, scheduled, messagesOnly, withoutEdges,
                        ORDER_CREATED, SHIP_ORDER, POST_ORDER, PUT_STOCK, NIGHTLY_JOB),
                List.of(
                        new Trigger(POST_ORDER, restTriggered, 3),
                        new Action(restTriggered, ORDER_CREATED),
                        new Action(restTriggered, PUT_STOCK),
                        new Trigger(PUT_STOCK, triggerOnlyRest, 1),
                        new Trigger(NIGHTLY_JOB, scheduled, 1),
                        new Action(scheduled, SHIP_ORDER),
                        new Trigger(ORDER_CREATED, messagesOnly, 3),
                        new Action(messagesOnly, SHIP_ORDER)));

        Graph filtered = MessageGraphFilter.filter(graph);

        assertThat(filtered.nodes()).containsExactly(
                restTriggered, scheduled, messagesOnly, withoutEdges, ORDER_CREATED, SHIP_ORDER);
        assertThat(filtered.edges()).containsExactly(
                new Action(restTriggered, ORDER_CREATED),
                new Action(scheduled, SHIP_ORDER),
                new Trigger(ORDER_CREATED, messagesOnly, 3),
                new Action(messagesOnly, SHIP_ORDER));
    }

    @Test
    void filter_removesANonMessageInterfaceNodeWithNoEdgeAtAll_insteadOfTakingTheFastPath() {
        // No edge refers to NIGHTLY_JOB: the fast path must not mistake this for "nothing to filter"
        Reaction messagesOnly = reaction(10);
        Graph graph = new Graph(
                List.of(messagesOnly, ORDER_CREATED, NIGHTLY_JOB),
                List.of(new Trigger(ORDER_CREATED, messagesOnly, 3)));

        Graph filtered = MessageGraphFilter.filter(graph);

        assertThat(filtered.nodes()).containsExactly(messagesOnly, ORDER_CREATED);
        assertThat(filtered.edges()).containsExactly(new Trigger(ORDER_CREATED, messagesOnly, 3));
    }

    private static Reaction reaction(long id) {
        return new Reaction(id, "component" + id, "system", 3);
    }
}
