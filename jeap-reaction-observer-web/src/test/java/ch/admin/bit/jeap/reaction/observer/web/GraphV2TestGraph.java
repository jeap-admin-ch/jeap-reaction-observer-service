package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Action;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.GenericInterface;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Graph;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.HttpOperation;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Reaction;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.SemanticType;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Trigger;

import java.util.List;

/**
 * A graph with every kind of interface, for the tests of the API v2:
 * <pre>
 *   [OrderSubmitted I11] --> (order-service R11) --> [OrderChecked/eu I12], [POST /api/credits I14], [nightly-job I16]
 *   [POST /api/credits I14] --> (partner-service R12) --> [CreditReserved I13]
 *   [POST /api/orders I15] --> (order-service R13) --> [OrderSubmitted I11]
 * </pre>
 * Interfaces and reactions share the database ids 11, 12 and 13 on purpose: they come from two tables.
 */
public final class GraphV2TestGraph {

    public static final Message SUBMITTED = new Message(11, "OrderSubmitted", null, SemanticType.EVENT);
    public static final Message CHECKED = new Message(12, "OrderChecked", "eu", SemanticType.EVENT);
    public static final Message RESERVED = new Message(13, "CreditReserved", null, SemanticType.EVENT);
    public static final HttpOperation RESERVE_CREDIT =
            new HttpOperation(14, "partner-service", "POST", "/api/credits");
    public static final HttpOperation START_ORDER = new HttpOperation(15, "order-service", "POST", "/api/orders");
    public static final GenericInterface NIGHTLY_JOB = new GenericInterface(16, "scheduled", "nightly-job");

    public static final Reaction CHECK_ORDER =
            Reaction.builder().id(11).component("order-service").system("shop").median(7).build();
    public static final Reaction RESERVE =
            Reaction.builder().id(12).component("partner-service").system("shop").median(7).build();
    public static final Reaction START =
            Reaction.builder().id(13).component("order-service").system("shop").median(3).build();

    private GraphV2TestGraph() {
    }

    public static Graph graph() {
        return new Graph(
                List.of(SUBMITTED, CHECKED, RESERVED, RESERVE_CREDIT, START_ORDER, NIGHTLY_JOB, CHECK_ORDER, RESERVE,
                        START),
                List.of(Trigger.builder().source(SUBMITTED).target(CHECK_ORDER).median(7).build(),
                        Action.builder().source(CHECK_ORDER).target(CHECKED).build(),
                        Action.builder().source(CHECK_ORDER).target(RESERVE_CREDIT).build(),
                        Action.builder().source(CHECK_ORDER).target(NIGHTLY_JOB).build(),
                        Trigger.builder().source(RESERVE_CREDIT).target(RESERVE).median(7).build(),
                        Action.builder().source(RESERVE).target(RESERVED).build(),
                        Trigger.builder().source(START_ORDER).target(START).median(3).build(),
                        Action.builder().source(START).target(SUBMITTED).build()));
    }
}
