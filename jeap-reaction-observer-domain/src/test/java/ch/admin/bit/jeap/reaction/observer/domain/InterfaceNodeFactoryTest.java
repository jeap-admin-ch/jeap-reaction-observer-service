package ch.admin.bit.jeap.reaction.observer.domain;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.GenericInterface;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.HttpOperation;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.Message;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.SemanticType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceNodeFactoryTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final InterfaceNodeFactory factory = new InterfaceNodeFactory(new ReactionObserverMetrics(meterRegistry));

    @Test
    void create_event_isAMessage() {
        assertThat(factory.create(1, "event", "OrderCreated"))
                .isEqualTo(new Message(1, "OrderCreated", null, SemanticType.EVENT));
    }

    @Test
    void create_commandWithVariant_isAMessageWithTheVariant_ignoringTheCaseOfTheType() {
        assertThat(factory.create(2, "COMMAND", "ShipOrder/express"))
                .isEqualTo(new Message(2, "ShipOrder", "express", SemanticType.COMMAND));
    }

    @Test
    void create_messageWithFurtherSegments_isSplitAsBefore() {
        assertThat(factory.create(3, "event", "Type/variant/ignored"))
                .isEqualTo(new Message(3, "Type", "variant", SemanticType.EVENT));
    }

    @Test
    void create_httpRequest_isAnHttpOperation_splitAtTheFirstTwoSpaces() {
        assertThat(factory.create(4, "http_request", "order-service GET /api/orders/{id} with space"))
                .isEqualTo(new HttpOperation(4, "order-service", "GET", "/api/orders/{id} with space"));
        assertThat(unparsableInterfaces("http_request")).isZero();
    }

    @Test
    void create_unknownType_isAGenericInterface_andNotCountedAsUnparsable() {
        assertThat(factory.create(5, "scheduled", "nightly-job"))
                .isEqualTo(new GenericInterface(5, "scheduled", "nightly-job"));
        assertThat(meterRegistry.find(ReactionObserverMetrics.UNPARSABLE_INTERFACES).counters())
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "order-service", "order-service GET", " GET /api", "order-service  /api", "",
            // A stray space right after METHOD or at the end must be rejected the same way as one before METHOD,
            // instead of leaking into the path as a leading or trailing space
            "order-service GET  /api/orders", "order-service GET /api/orders "})
    void create_unparsableHttpRequest_isAGenericInterface_andCounted(String fqn) {
        assertThat(factory.create(6, "http_request", fqn))
                .isEqualTo(new GenericInterface(6, "http_request", fqn));
        assertThat(unparsableInterfaces("http_request")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "/variant"})
    void create_unparsableMessage_isAGenericInterface_andCounted(String fqn) {
        assertThat(factory.create(7, "event", fqn))
                .isEqualTo(new GenericInterface(7, "event", fqn));
        assertThat(unparsableInterfaces("event")).isEqualTo(1);
    }

    @Test
    void create_unparsable_isCountedOncePerInterface_notOnEveryGraphRefresh() {
        factory.create(8, "http_request", "garbage");
        factory.create(8, "http_request", "garbage");
        factory.create(9, "HTTP_REQUEST", "other-garbage");

        assertThat(unparsableInterfaces("http_request")).isEqualTo(2);
    }

    @Test
    void create_nullType_isAGenericInterface() {
        assertThat(factory.create(10, null, "something"))
                .isEqualTo(new GenericInterface(10, null, "something"));
    }

    private double unparsableInterfaces(String type) {
        Counter counter = meterRegistry.find(ReactionObserverMetrics.UNPARSABLE_INTERFACES).tag("type", type).counter();
        return counter == null ? 0 : counter.count();
    }
}
