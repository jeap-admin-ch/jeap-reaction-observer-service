package ch.admin.bit.jeap.reaction.observer.web.models.graph.v2;

import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * The id of a node in the API v2: a prefix for the kind of node and its database id, e.g. {@code I14} for an
 * interface and {@code R201} for a reaction.
 * <p>
 * The database ids are unique per table only, so an interface and a reaction can have the same one; with the prefix
 * an id is unique within a response. The prefix names the <b>table</b>, not the {@code nodeType}: an interface
 * presented in a new way keeps its id, its edges and its path.
 */
public final class NodeV2Id {

    private static final String INTERFACE_PREFIX = "I";
    private static final String REACTION_PREFIX = "R";
    /** As many digits as a long can have; whether they are a long is found out by parsing them */
    private static final Pattern INTERFACE_ID = Pattern.compile(INTERFACE_PREFIX + "\\d{1,19}");

    private NodeV2Id() {
    }

    public static String ofInterface(long id) {
        return INTERFACE_PREFIX + id;
    }

    public static String ofReaction(long id) {
        return REACTION_PREFIX + id;
    }

    /**
     * The database id of an interface from its node id, or empty when it is not the id of an interface.
     * <p>
     * The inverse of {@link #ofInterface}: every id that method writes is read, and nothing else is - not a number
     * beyond a long, and not another spelling of an id such as {@code I014}, so that an interface has one path.
     */
    public static OptionalLong parseInterfaceId(String nodeId) {
        if (nodeId == null || !INTERFACE_ID.matcher(nodeId).matches()) {
            return OptionalLong.empty();
        }
        long id;
        try {
            id = Long.parseLong(nodeId.substring(INTERFACE_PREFIX.length()));
        } catch (NumberFormatException e) {
            // 19 digits that are more than the largest long
            return OptionalLong.empty();
        }
        return ofInterface(id).equals(nodeId) ? OptionalLong.of(id) : OptionalLong.empty();
    }
}
