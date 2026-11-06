package ch.admin.bit.jeap.reaction.observer.persistence;

import ch.admin.bit.jeap.reaction.observer.domain.InterfaceNodeFactory;
import ch.admin.bit.jeap.reaction.observer.domain.ReactionGraphRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReactionGraphRepositoryImpl implements ReactionGraphRepository {

    private final JpaReactionRepository jpaReactionRepository;
    private final InterfaceNodeFactory interfaceNodeFactory;

    public ReactionGraphRepositoryImpl(JpaReactionRepository jpaReactionRepository,
                                       InterfaceNodeFactory interfaceNodeFactory) {
        this.jpaReactionRepository = jpaReactionRepository;
        this.interfaceNodeFactory = interfaceNodeFactory;
    }

    @Override
    public Graph buildFullGraph(Map<Long, Integer> medians) {
        List<ReactionEntity> entities = jpaReactionRepository.findAllWithActions();

        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        Map<Long, Interface> interfaces = new HashMap<>();

        for (ReactionEntity entity : entities) {
            // The median belongs to the reaction, so the node carries it as well as the trigger edge - a
            // reaction nothing triggered has no edge, and its number would otherwise be dropped here.
            Integer median = medians.get(entity.getId());

            // Reaction node
            Reaction reaction = Reaction.builder()
                    .id(entity.getId())
                    .component(entity.getComponent())
                    .system(entity.getSystem())
                    .median(median)
                    .build();
            nodes.add(reaction);

            // Trigger edge and node
            InterfaceEntity triggerInterface = entity.getTriggerInterface();
            if (triggerInterface != null) {
                Interface triggerNode = interfaces.computeIfAbsent(triggerInterface.getId(),
                        id -> createInterface(triggerInterface));
                edges.add(Trigger.builder()
                        .source(triggerNode)
                        .target(reaction)
                        .median(median)
                        .build());
            }

            // Action edge and nodes
            for (ActionEntity actionEntity : entity.getActions()) {
                InterfaceEntity actionInterface = actionEntity.getActionInterface();
                Interface actionNode = interfaces.computeIfAbsent(actionInterface.getId(),
                        id -> createInterface(actionInterface));
                edges.add(Action.builder()
                        .source(reaction)
                        .target(actionNode)
                        .build());
            }
        }

        nodes.addAll(interfaces.values());
        return new Graph(nodes, edges);
    }

    private Interface createInterface(InterfaceEntity iface) {
        return interfaceNodeFactory.create(iface.getId(), iface.getType(), iface.getFqn());
    }
}
