package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ReactionGraphRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceAutoConfiguration.class)
@Slf4j
class ReactionGraphRepositoryImplTest {

    @Autowired
    private JpaReactionRepository jpaReactionRepository;

    @Autowired
    private JpaInterfaceRepository jpaInterfaceRepository;

    private ReactionGraphRepository reactionGraphRepository;

    @BeforeEach
    void setUp() {
        reactionGraphRepository = new ReactionGraphRepositoryImpl(jpaReactionRepository);
    }

    @Test
    void buildFullGraph_triggerOnly() {
        InterfaceEntity triggerInterface = jpaInterfaceRepository.save(new InterfaceEntity("EVENT", "trigger/only"));

        ReactionEntity reaction = ReactionEntity.builder()
                .system("systemT")
                .component("componentT")
                .reactionId("reactionTriggerOnly")
                .triggerId("t1")
                .triggerInterface(triggerInterface)
                .identifiedAt(ZonedDateTime.now())
                .build();

        ReactionEntity savedReaction = jpaReactionRepository.save(reaction);
        Long reactionDbId = savedReaction.getId();
        Long triggerInterfaceId = triggerInterface.getId();

        Graph graph = reactionGraphRepository.buildFullGraph();

        // Reaction node
        Optional<Node> reactionNode = graph.nodes().stream()
                .filter(n -> n instanceof Reaction && ((Reaction) n).getId() == reactionDbId)
                .findFirst();
        assertThat(reactionNode).isPresent();

        // Trigger message node
        Optional<Message> triggerMessage = graph.nodes().stream()
                .filter(n -> n instanceof Message && ((Message) n).getId() == triggerInterfaceId)
                .map(n -> (Message) n)
                .findFirst();
        assertThat(triggerMessage).isPresent();
        assertThat(triggerMessage.get().messageType()).isEqualTo("trigger");
        assertThat(triggerMessage.get().semantic()).isEqualTo(SemanticType.EVENT);

        // Trigger edge
        assertThat(graph.edges()).hasSize(1);
        assertThat(graph.edges().getFirst()).isInstanceOf(Trigger.class);
    }

    @Test
    void buildFullGraph_actionOnly() {
        InterfaceEntity actionInterface = jpaInterfaceRepository.save(new InterfaceEntity("COMMAND", "action/only"));

        ReactionEntity reaction = ReactionEntity.builder()
                .system("systemA")
                .component("componentA")
                .reactionId("reactionActionOnly")
                .identifiedAt(ZonedDateTime.now())
                .build();

        ActionEntity action = ActionEntity.builder()
                .reaction(reaction)
                .actionId("a1")
                .actionInterface(actionInterface)
                .build();

        reaction.addAction(action);
        ReactionEntity savedReaction = jpaReactionRepository.save(reaction);

        Graph graph = reactionGraphRepository.buildFullGraph();

        // Reaction node
        Optional<Node> reactionNode = graph.nodes().stream()
                .filter(n -> n instanceof Reaction && ((Reaction) n).getId() == savedReaction.getId())
                .findFirst();
        assertThat(reactionNode).isPresent();

        // Action message node
        Optional<Message> actionMessage = graph.nodes().stream()
                .filter(n -> n instanceof Message && ((Message) n).getId() == actionInterface.getId())
                .map(n -> (Message) n)
                .findFirst();
        assertThat(actionMessage).isPresent();
        assertThat(actionMessage.get().messageType()).isEqualTo("action");
        assertThat(actionMessage.get().semantic()).isEqualTo(SemanticType.COMMAND);

        // Action edge
        assertThat(graph.edges()).hasSize(1);
        assertThat(graph.edges().getFirst()).isInstanceOf(Action.class);
    }


    @Test
    void buildFullGraph_multipleActions() {
        InterfaceEntity triggerInterface = jpaInterfaceRepository.save(new InterfaceEntity("EVENT", "trigger/multi"));
        InterfaceEntity actionInterface1 = jpaInterfaceRepository.save(new InterfaceEntity("COMMAND", "action/one"));
        InterfaceEntity actionInterface2 = jpaInterfaceRepository.save(new InterfaceEntity("COMMAND", "action/two"));

        ReactionEntity reaction = ReactionEntity.builder()
                .system("systemM")
                .component("componentM")
                .reactionId("reactionMultiAction")
                .triggerId("t1")
                .triggerInterface(triggerInterface)
                .identifiedAt(ZonedDateTime.now())
                .build();

        ActionEntity action1 = ActionEntity.builder()
                .reaction(reaction)
                .actionId("a1")
                .actionInterface(actionInterface1)
                .build();

        ActionEntity action2 = ActionEntity.builder()
                .reaction(reaction)
                .actionId("a2")
                .actionInterface(actionInterface2)
                .build();

        reaction.addAction(action1);
        reaction.addAction(action2);
        ReactionEntity savedReaction = jpaReactionRepository.save(reaction);

        Graph graph = reactionGraphRepository.buildFullGraph();

        // Reaction node
        Optional<Node> reactionNode = graph.nodes().stream()
                .filter(n -> n instanceof Reaction && ((Reaction) n).getId() == savedReaction.getId())
                .findFirst();
        assertThat(reactionNode).isPresent();

        // Message nodes
        List<Message> messages = graph.nodes().stream()
                .filter(n -> n instanceof Message)
                .map(n -> (Message) n)
                .toList();
        assertThat(messages)
                .hasSize(3)
                .anyMatch(m -> m.getId() == triggerInterface.getId())
                .anyMatch(m -> m.getId() == actionInterface1.getId())
                .anyMatch(m -> m.getId() == actionInterface2.getId());

        // Edges
        assertThat(graph.edges()).hasSize(3);
        assertThat(graph.edges()).filteredOn(Trigger.class::isInstance).hasSize(1);
        assertThat(graph.edges()).filteredOn(Action.class::isInstance).hasSize(2);
    }

}
