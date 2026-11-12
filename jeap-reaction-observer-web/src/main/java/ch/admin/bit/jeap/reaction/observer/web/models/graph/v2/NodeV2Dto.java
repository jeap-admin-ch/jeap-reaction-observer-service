package ch.admin.bit.jeap.reaction.observer.web.models.graph.v2;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * A node of a graph of the API v2: a reaction ({@link ReactionNodeV2Dto}) or an interface
 * ({@link InterfaceNodeV2Dto} and the kinds of interface the service presents in their own way).
 * <p>
 * Every kind of node is a class of its own with the fields of that kind only. The kinds are <b>not a closed
 * set</b>: an interface type presented in its own way later is a new {@code nodeType} and not a new version of the
 * API. A consumer reads a node with a {@code nodeType} it does not know as an {@link InterfaceNodeV2Dto} - every
 * interface node has its fields, whatever its kind.
 * <p>
 * The OpenAPI document has to say all of this for a client generated from it to work, which is what the
 * annotations here are for:
 * <ul>
 * <li>the discriminator mapping, because the values of {@code nodeType} are not the names of the schemas</li>
 * <li>the extension {@code x-jackson-default-impl}, from which the OpenAPI generator gives the generated node
 * class the Jackson default implementation that reads an unknown {@code nodeType}; its value is the name of the
 * schema of {@link InterfaceNodeV2Dto}</li>
 * <li>the subtypes nested as the classes are, so that the schema of a message extends the one of an interface</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "nodeType",
        visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ReactionNodeV2Dto.class, name = NodeV2Dto.REACTION),
        @JsonSubTypes.Type(value = InterfaceNodeV2Dto.class, name = NodeV2Dto.INTERFACE)
})
@Schema(description = "A node of the graph: a reaction or an interface. The kinds of node are not a closed set: "
                      + "read a node with an unknown nodeType as an interface, with its interfaceType and fqn.",
        discriminatorProperty = "nodeType",
        discriminatorMapping = {
                @DiscriminatorMapping(value = NodeV2Dto.REACTION, schema = ReactionNodeV2Dto.class),
                @DiscriminatorMapping(value = NodeV2Dto.INTERFACE, schema = InterfaceNodeV2Dto.class),
                @DiscriminatorMapping(value = NodeV2Dto.MESSAGE, schema = MessageNodeV2Dto.class),
                @DiscriminatorMapping(value = NodeV2Dto.HTTP_OPERATION, schema = HttpOperationNodeV2Dto.class)
        },
        extensions = @Extension(name = "", properties = @ExtensionProperty(
                name = "x-jackson-default-impl", value = "InterfaceNodeV2Dto")))
@Getter
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode
@ToString
public abstract class NodeV2Dto {

    public static final String REACTION = "REACTION";
    public static final String INTERFACE = "INTERFACE";
    public static final String MESSAGE = "MESSAGE";
    public static final String HTTP_OPERATION = "HTTP_OPERATION";

    @Schema(description = "Id of the node, unique within the response: I<number> for an interface, R<number> for "
                          + "a reaction. Not to be stored for long; see the consumer contract.",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "I14")
    private final String id;

    @Schema(description = "Kind of node. Known values: REACTION, INTERFACE, MESSAGE, HTTP_OPERATION. Not a closed "
                          + "set: further kinds of interface nodes can be added.",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "HTTP_OPERATION")
    public abstract String getNodeType();
}
