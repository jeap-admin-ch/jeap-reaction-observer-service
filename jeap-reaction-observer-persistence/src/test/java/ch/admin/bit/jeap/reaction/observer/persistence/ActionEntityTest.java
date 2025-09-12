package ch.admin.bit.jeap.reaction.observer.persistence;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ActionEntityTest {

    @Test
    void shouldBuildActionEntityCorrectly() {
        // given
        InterfaceEntity interfaceEntity = new InterfaceEntity("type", "fqn");
        ReactionEntity reactionEntity = ReactionEntity.builder()
                .system("sys")
                .component("comp")
                .reactionId("react-1")
                .identifiedAt(ZonedDateTime.now())
                .build();

        // when
        ActionEntity actionEntity = ActionEntity.builder()
                .reaction(reactionEntity)
                .actionId("action-123")
                .actionInterface(interfaceEntity)
                .build();

        // then
        assertThat(actionEntity.getActionId()).isEqualTo("action-123");
        assertThat(actionEntity.getReaction()).isEqualTo(reactionEntity);
        assertThat(actionEntity.getActionInterface()).isEqualTo(interfaceEntity);
    }

    @Test
    void shouldCompareEntitiesById() {
        // given
        ActionEntity a1 = new ActionEntity(null, "a1", null);
        ActionEntity a2 = new ActionEntity(null, "a2", null);

        // when
        setId(a1, 1L);
        setId(a2, 1L);

        // then
        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }

    @Test
    void shouldNotBeEqualIfIdsDiffer() {
        ActionEntity a1 = new ActionEntity(null, "a1", null);
        ActionEntity a2 = new ActionEntity(null, "a2", null);

        setId(a1, 1L);
        setId(a2, 2L);

        assertThat(a1).isNotEqualTo(a2);
    }

    @Test
    void shouldNotBeEqualToNullOrDifferentClass() {
        ActionEntity a1 = new ActionEntity(null, "a1", null);
        setId(a1, 1L);

        assertThat(a1).isNotEqualTo(null);
        assertThat(a1).isNotEqualTo("some string");
    }

    // Helper to set private ID field via reflection
    private void setId(ActionEntity entity, Long id) {
        try {
            var field = ActionEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
