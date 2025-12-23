package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.ReactionGraphRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReactionGraphRepositoryImpl implements ReactionGraphRepository {

    private final JpaReactionRepository jpaReactionRepository;

    public ReactionGraphRepositoryImpl(JpaReactionRepository jpaReactionRepository) {
        this.jpaReactionRepository = jpaReactionRepository;
    }

    @Override
    public Graph buildFullGraph() {
        List<ReactionEntity> entities = jpaReactionRepository.findAllWithActions();

        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        Map<Long, Interface> interfaces = new HashMap<>();

        for (ReactionEntity entity : entities) {
            // Reaction node
            Reaction reaction = Reaction.builder()
                    .id(entity.getId())
                    .component(entity.getComponent())
                    .system(entity.getSystem())
                    .build();
            nodes.add(reaction);

            // Trigger edge and node
            InterfaceEntity triggerInterface = entity.getTriggerInterface();
            if (triggerInterface != null) {
                Message triggerMessage = (Message) interfaces.computeIfAbsent(triggerInterface.getId(), id -> createMessage(triggerInterface));
                edges.add(Trigger.builder()
                        .source(triggerMessage)
                        .target(reaction)
                        .build());
            }

            // Action edge and nodes
            for (ActionEntity actionEntity : entity.getActions()) {
                InterfaceEntity actionInterface = actionEntity.getActionInterface();
                Message actionMessage = (Message) interfaces.computeIfAbsent(actionInterface.getId(), id -> createMessage(actionInterface));
                edges.add(Action.builder()
                        .source(reaction)
                        .target(actionMessage)
                        .build());
            }
        }

        nodes.addAll(interfaces.values());
        return new Graph(nodes, edges);
    }

    private Message createMessage(InterfaceEntity iface) {
        String[] fqnParts = iface.getFqn().contains("/") ? iface.getFqn().split("/") : new String[]{iface.getFqn(), null};
        return Message.builder()
                .id(iface.getId())
                .messageType(fqnParts[0])
                .variant(fqnParts.length > 1 ? fqnParts[1] : null)
                .semantic(SemanticType.fromValue(iface.getType()))
                .build();
    }
}
