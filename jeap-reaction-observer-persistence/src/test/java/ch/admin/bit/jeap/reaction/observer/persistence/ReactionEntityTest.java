package ch.admin.bit.jeap.reaction.observer.persistence;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReactionEntityTest {

    @Test
    void shouldBuildReactionEntityCorrectly() {
        InterfaceEntity triggerInterface = InterfaceEntity.builder()
                .type("REST")
                .fqn("ch.admin.bit.Trigger")
                .build();

        ZonedDateTime now = ZonedDateTime.now();

        ReactionEntity reaction = ReactionEntity.builder()
                .reactionId("r-123")
                .component("comp")
                .system("sys")
                .triggerId("t-1")
                .triggerInterface(triggerInterface)
                .identifiedAt(now)
                .build();

        assertThat(reaction.getReactionId()).isEqualTo("r-123");
        assertThat(reaction.getComponent()).isEqualTo("comp");
        assertThat(reaction.getSystem()).isEqualTo("sys");
        assertThat(reaction.getTriggerId()).isEqualTo("t-1");
        assertThat(reaction.getTriggerInterface()).isEqualTo(triggerInterface);
        assertThat(reaction.getIdentifiedAt()).isEqualTo(now);
    }

    @Test
    void shouldAddActionAndSetBackReference() {
        ReactionEntity reaction = ReactionEntity.builder()
                .reactionId("r-1")
                .component("comp")
                .identifiedAt(ZonedDateTime.now())
                .build();

        ActionEntity action = ActionEntity.builder()
                .actionId("a-1")
                .actionInterface(InterfaceEntity.builder().type("REST").fqn("fqn").build())
                .build();

        reaction.addAction(action);

        assertThat(reaction.getActions()).containsExactly(action);
        assertThat(action.getReaction()).isEqualTo(reaction);
    }

    @Test
    void shouldCompareEntitiesById() {
        ReactionEntity r1 = ReactionEntity.builder()
                .reactionId("r1")
                .component("c")
                .identifiedAt(ZonedDateTime.now())
                .build();

        ReactionEntity r2 = ReactionEntity.builder()
                .reactionId("r2")
                .component("c")
                .identifiedAt(ZonedDateTime.now())
                .build();

        setId(r1, 1L);
        setId(r2, 1L);

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    private void setId(ReactionEntity entity, Long id) {
        try {
            var field = ReactionEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
