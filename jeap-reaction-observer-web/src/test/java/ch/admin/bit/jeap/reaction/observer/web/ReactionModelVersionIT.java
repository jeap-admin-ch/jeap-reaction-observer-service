package ch.admin.bit.jeap.reaction.observer.web;

import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionIdentifiedV2EventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionsObservedEventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestUnidentifiedActions;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The reaction model version and the unidentified actions the emitters report arrive through Kafka and are stored.
 */
class ReactionModelVersionIT extends IntegrationTestBase {

    private static final String SYSTEM = "system";
    private static final String COMPONENT = "test1";
    private static final TestReaction REACTION = new TestReaction(
            TestObservation.ofEvent("MyEvent"), List.of(TestObservation.ofCommand("MyCommand")), "reaction1");

    @Test
    void everyIdentificationStoresItsReactionModelVersion_andAKnownReactionKeepsItsIdentificationTimestamp() {
        // given: a reaction identified with reaction model version 2
        sendSync("reaction-identified", ReactionIdentifiedV2EventBuilder.buildEvent(SYSTEM, COMPONENT, REACTION, 2));
        await().until(() -> lastIdentifiedByModelVersion() != null && lastIdentifiedByModelVersion() == 2);
        Timestamp identifiedAt = identifiedAt();

        // when: it is identified again, with reaction model version 3
        sendSync("reaction-identified", ReactionIdentifiedV2EventBuilder.buildEvent(SYSTEM, COMPONENT, REACTION, 3));

        // then: the version is replaced and the identification timestamp is unchanged
        await().until(() -> lastIdentifiedByModelVersion() == 3);
        assertThat(identifiedAt()).isEqualTo(identifiedAt);
    }

    @Test
    void anEventWithoutReactionModelVersion_isStoredAsModelVersion1() {
        sendAndAwaitReactionPersistence(REACTION, SYSTEM, COMPONENT);

        assertThat(lastIdentifiedByModelVersion()).isEqualTo(1);
    }

    @Test
    void aCountStoresTheReactionModelVersionAndTheUnidentifiedActions() {
        // given: a known reaction
        sendAndAwaitReactionPersistence(REACTION, SYSTEM, COMPONENT);

        // when: a count with a reaction model version and unidentified actions arrives
        Instant now = Instant.now();
        ReactionsObservedEvent event = new ReactionsObservedEventBuilder(COMPONENT, SYSTEM)
                .serviceInstanceIdentifier(UUID.randomUUID())
                .countByReactionId(Map.of(REACTION.id(), 10))
                .reactionModelVersion(2)
                .unidentifiedActionsByReactionId(Map.of(REACTION.id(), new TestUnidentifiedActions(1, 3, 7)))
                .timeframe(now.minusSeconds(300), now)
                .build();
        sendSync("reactions-observed", event);

        // then: all values are stored
        String idempotenceId = event.getIdentity().getIdempotenceId();
        await().until(() -> observedReactionIsPersisted(idempotenceId) == 1);
        assertThat(storedCount(idempotenceId))
                .containsEntry("reaction_model_version", 2)
                .containsEntry("min_unidentified_actions", 1)
                .containsEntry("observations_with_unidentified_actions", 3)
                .containsEntry("unidentified_actions", 7);
    }

    @Test
    void aCountWithoutTheNewValues_isStoredAsModelVersion1WithoutUnidentifiedActions() {
        // given: a known reaction
        sendAndAwaitReactionPersistence(REACTION, SYSTEM, COMPONENT);

        // when: a count without a reaction model version and unidentified actions arrives
        ReactionsObservedEvent event = createReactionsObservedEvent(REACTION);
        sendSync("reactions-observed", event);

        // then: the defaults are stored
        String idempotenceId = event.getIdentity().getIdempotenceId();
        await().until(() -> observedReactionIsPersisted(idempotenceId) == 1);
        assertThat(storedCount(idempotenceId))
                .containsEntry("reaction_model_version", 1)
                .containsEntry("min_unidentified_actions", 0)
                .containsEntry("observations_with_unidentified_actions", 0)
                .containsEntry("unidentified_actions", 0);
    }

    private Integer lastIdentifiedByModelVersion() {
        return jdbcTemplate.query("select last_identified_by_model_version from reaction where component = ? and reaction_id = ?",
                        (row, _) -> row.getInt(1), COMPONENT, REACTION.id())
                .stream().findFirst().orElse(null);
    }

    private Timestamp identifiedAt() {
        return jdbcTemplate.queryForObject("select identified_at from reaction where component = ? and reaction_id = ?",
                Timestamp.class, COMPONENT, REACTION.id());
    }

    private Map<String, Object> storedCount(String idempotenceId) {
        return jdbcTemplate.queryForMap("""
                select reaction_model_version, min_unidentified_actions, observations_with_unidentified_actions, unidentified_actions
                from observed_reaction where idempotence_id = ?""", idempotenceId);
    }
}
