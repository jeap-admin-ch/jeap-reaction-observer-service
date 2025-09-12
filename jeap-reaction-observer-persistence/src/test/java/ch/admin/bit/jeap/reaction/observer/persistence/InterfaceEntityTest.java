package ch.admin.bit.jeap.reaction.observer.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceEntityTest {

    @Test
    void shouldBuildInterfaceEntityCorrectly() {
        InterfaceEntity entity = InterfaceEntity.builder()
                .type("REST")
                .fqn("ch.admin.bit.service.MyService")
                .build();

        assertThat(entity.getType()).isEqualTo("REST");
        assertThat(entity.getFqn()).isEqualTo("ch.admin.bit.service.MyService");
    }

    @Test
    void shouldCompareEntitiesById() {
        InterfaceEntity i1 = InterfaceEntity.builder().type("REST").fqn("a").build();
        InterfaceEntity i2 = InterfaceEntity.builder().type("SOAP").fqn("b").build();

        setId(i1, 1L);
        setId(i2, 1L);

        assertThat(i1).isEqualTo(i2);
        assertThat(i1.hashCode()).isEqualTo(i2.hashCode());
    }

    @Test
    void shouldNotBeEqualIfIdsDiffer() {
        InterfaceEntity i1 = InterfaceEntity.builder().type("REST").fqn("a").build();
        InterfaceEntity i2 = InterfaceEntity.builder().type("REST").fqn("a").build();

        setId(i1, 1L);
        setId(i2, 2L);

        assertThat(i1).isNotEqualTo(i2);
    }

    private void setId(InterfaceEntity entity, Long id) {
        try {
            var field = InterfaceEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
