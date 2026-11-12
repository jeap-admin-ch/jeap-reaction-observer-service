package ch.admin.bit.jeap.reaction.observer.web.models.graph.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NodeV2IdTest {

    /** The path of an interface graph is built from the id the service writes, so every such id has to be read. */
    @ParameterizedTest
    @ValueSource(longs = {0, 1, 14, 999_999_999_999_999_999L, 1_000_000_000_000_000_000L, Long.MAX_VALUE})
    void parseInterfaceId_readsEveryIdTheServiceWrites(long id) {
        assertThat(NodeV2Id.parseInterfaceId(NodeV2Id.ofInterface(id))).hasValue(id);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "I", "14", "R14", "i14", "I-14", "I1.4", "I14 ", "I014", "I00",
            "I9223372036854775808", "I99999999999999999999"})
    void parseInterfaceId_isEmptyForAnythingElse(String nodeId) {
        assertThat(NodeV2Id.parseInterfaceId(nodeId)).isEmpty();
    }

    @Test
    void theIdsOfAnInterfaceAndAReactionWithTheSameDatabaseId_differ() {
        assertThat(NodeV2Id.ofInterface(14)).isEqualTo("I14");
        assertThat(NodeV2Id.ofReaction(14)).isEqualTo("R14");
    }
}
