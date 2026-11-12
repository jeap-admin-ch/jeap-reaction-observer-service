package ch.admin.bit.jeap.reaction.observer.web.models.graph.v2;

import ch.admin.bit.jeap.reaction.observer.domain.models.graph.HttpOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * The node of an HTTP operation of the API v2: an interface of the type {@code http_request}.
 */
@Schema(description = "An HTTP operation: an interface of the type http_request")
@Getter
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HttpOperationNodeV2Dto extends InterfaceNodeV2Dto {

    @Schema(description = "The service providing the operation. Starts with // when the caller named the "
                          + "operation because the called service did not: it then is where the call went, "
                          + "e.g. //foo.bar.admin.ch, and the path is /{*}.")
    private final String provider;
    @Schema(description = "The HTTP method")
    private final String method;
    @Schema(description = "The path pattern of the operation")
    private final String path;

    @Override
    public String getNodeType() {
        return HTTP_OPERATION;
    }

    public static HttpOperationNodeV2Dto from(HttpOperation operation) {
        return builder()
                .id(NodeV2Id.ofInterface(operation.id()))
                .interfaceType(operation.type())
                .fqn(operation.fqn())
                .provider(operation.provider())
                .method(operation.method())
                .path(operation.path())
                .build();
    }
}
