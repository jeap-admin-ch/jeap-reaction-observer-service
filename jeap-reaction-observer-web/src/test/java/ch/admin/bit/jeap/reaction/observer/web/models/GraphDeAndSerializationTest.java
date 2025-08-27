package ch.admin.bit.jeap.reaction.observer.web.models;

import ch.admin.bit.jeap.reaction.observer.web.models.graph.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphDeAndSerializationTest {

    private static final long MESSAGE_ID = 123L;
    private static final String MESSAGE_KEY = "msg_123";
    private static final String MESSAGE_TYPE = "MyEvent";
    private static final String MESSAGE_VARIANT = "default";

    private static final long REACTION_ID = 77L;
    private static final String COMPONENT_NAME = "test-component";

    private static final int MEDIAN = 10;

    @Test
    void testGraphSerializationAndDeserialization() throws Exception {
        Graph graph = new Graph(
                List.of(
                        new MessageNode(NodeType.MESSAGE, MESSAGE_ID, MESSAGE_KEY, MESSAGE_TYPE, MESSAGE_VARIANT),
                        new ReactionNode(NodeType.REACTION, REACTION_ID, COMPONENT_NAME)
                ),
                List.of(
                        new TriggerEdge(EdgeType.TRIGGER, MESSAGE_ID, NodeType.MESSAGE, REACTION_ID, MEDIAN),
                        new ActionEdge(EdgeType.ACTION, REACTION_ID, MESSAGE_ID, NodeType.REACTION)
                )
        );

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(graph);
        Graph deserialized = mapper.readValue(json, Graph.class);

        assertEquals(2, deserialized.nodes().size());
        assertEquals(2, deserialized.edges().size());

        MessageNode desMsg = (MessageNode) deserialized.nodes().getFirst();
        assertEquals(NodeType.MESSAGE, desMsg.nodeType());
        assertEquals(MESSAGE_ID, desMsg.id());
        assertEquals(MESSAGE_KEY, desMsg.messageKey());
        assertEquals(MESSAGE_TYPE, desMsg.messageType());
        assertEquals(MESSAGE_VARIANT, desMsg.variant());

        ReactionNode desReact = (ReactionNode) deserialized.nodes().get(1);
        assertEquals(NodeType.REACTION, desReact.nodeType());
        assertEquals(REACTION_ID, desReact.id());
        assertEquals(COMPONENT_NAME, desReact.component());

        TriggerEdge desTrig = (TriggerEdge) deserialized.edges().getFirst();
        assertEquals(EdgeType.TRIGGER, desTrig.edgeType());
        assertEquals(MESSAGE_ID, desTrig.sourceId());
        assertEquals(NodeType.MESSAGE, desTrig.sourceNodeType());
        assertEquals(REACTION_ID, desTrig.targetReactionId());
        assertEquals(MEDIAN, desTrig.median());

        ActionEdge desAct = (ActionEdge) deserialized.edges().get(1);
        assertEquals(EdgeType.ACTION, desAct.edgeType());
        assertEquals(REACTION_ID, desAct.sourceReactionId());
        assertEquals(MESSAGE_ID, desAct.targetId());
    }

    @Test
    void testDeserializationFromJsonString() throws Exception {
        String json =
                "{" +
                "  \"nodes\": [" +
                "    {" +
                "      \"nodeType\": \"" + NodeType.MESSAGE.name() + "\"," +
                "      \"id\": " + MESSAGE_ID + "," +
                "      \"messageKey\": \"" + MESSAGE_KEY + "\"," +
                "      \"messageType\": \"" + MESSAGE_TYPE + "\"," +
                "      \"variant\": \"" + MESSAGE_VARIANT + "\"" +
                "    }," +
                "    {" +
                "      \"nodeType\": \"" + NodeType.REACTION.name() + "\"," +
                "      \"id\": " + REACTION_ID + "," +
                "      \"component\": \"" + COMPONENT_NAME + "\"" +
                "    }" +
                "  ]," +
                "  \"edges\": [" +
                "    {" +
                "      \"edgeType\": \"" + EdgeType.TRIGGER.name() + "\"," +
                "      \"sourceId\": " + MESSAGE_ID + "," +
                "      \"sourceNodeType\": \"" + NodeType.MESSAGE.name() + "\"," +
                "      \"targetReactionId\": " + REACTION_ID + "," +
                "      \"median\": " + MEDIAN + "" +
                "    }," +
                "    {" +
                "      \"edgeType\": \"" + EdgeType.ACTION.name() + "\"," +
                "      \"sourceReactionId\": " + REACTION_ID + "," +
                "      \"targetId\": " + MESSAGE_ID + "," +
                "      \"targetNodeType\": \"" + NodeType.REACTION.name() + "\"" +
                "    }" +
                "  ]" +
                "}";

        ObjectMapper mapper = new ObjectMapper();
        Graph graph = mapper.readValue(json, Graph.class);

        assertEquals(2, graph.nodes().size());
        assertEquals(2, graph.edges().size());

        MessageNode msg = (MessageNode) graph.nodes().getFirst();
        assertEquals(MESSAGE_ID, msg.id());
        assertEquals(MESSAGE_KEY, msg.messageKey());
        assertEquals(MESSAGE_TYPE, msg.messageType());
        assertEquals(MESSAGE_VARIANT, msg.variant());

        ReactionNode react = (ReactionNode) graph.nodes().get(1);
        assertEquals(REACTION_ID, react.id());
        assertEquals(COMPONENT_NAME, react.component());

        TriggerEdge trig = (TriggerEdge) graph.edges().getFirst();
        assertEquals(MESSAGE_ID, trig.sourceId());
        assertEquals(NodeType.MESSAGE, trig.sourceNodeType());
        assertEquals(REACTION_ID, trig.targetReactionId());
        assertEquals(MEDIAN, trig.median());

        ActionEdge act = (ActionEdge) graph.edges().get(1);
        assertEquals(REACTION_ID, act.sourceReactionId());
        assertEquals(MESSAGE_ID, act.targetId());
    }
}