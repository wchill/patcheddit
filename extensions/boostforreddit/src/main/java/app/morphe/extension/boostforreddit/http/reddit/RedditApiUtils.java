package app.morphe.extension.boostforreddit.http.reddit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import app.morphe.extension.boostforreddit.utils.EditableObjectNode;
import app.morphe.extension.boostforreddit.utils.Emojis;

public class RedditApiUtils {
    public static ObjectNode createListing(List<? extends JsonNode> children) {
        ArrayNode childrenNode = new ArrayNode(JsonNodeFactory.instance);
        for (JsonNode child : children) {
            childrenNode.add(child);
        }
        EditableObjectNode root = new EditableObjectNode(Map.of(
                "kind", new TextNode("Listing"),
                "data", new EditableObjectNode(Map.of(
                        "after", NullNode.instance,
                        "dist", NullNode.instance,
                        "modhash", new TextNode(""),
                        "geo_filter", new TextNode(""),
                        "before", new TextNode(""),
                        "children", childrenNode
                ))
        ));
        return root;
    }

    public static EditableObjectNode createListing(JsonNode originalListing, List<? extends JsonNode> children) {
        JsonNode originalData = originalListing.get("data");
        EditableObjectNode data = new EditableObjectNode();
        List.of("after", "dist", "modhash", "geo_filter", "before").forEach(key -> {
            data.set(key, Optional.ofNullable(originalData.get(key)).orElse(NullNode.instance));
        });
        ArrayNode childrenNode = new ArrayNode(JsonNodeFactory.instance);
        for (JsonNode child : children) {
            childrenNode.add(child);
        }
        data.set("children", childrenNode);

        EditableObjectNode root = new EditableObjectNode();
        root.set("kind", new TextNode("Listing"));
        root.set("data", data);
        return root;
    }

    public static boolean isContentRemoved(JsonNode content) {
        return isRemovedByAdmins(content) || isRemovedByAntiSpam(content) || isRemovedByAntiEvilOps(content) || isRemovedByMod(content) || isDeletedByUser(content);
    }

    public static void setRemovalEmoji(ObjectNode originalNode) {
        if (isRemovedByAdmins(originalNode)) {
            originalNode.replace(Emojis.EXTRA_EMOJI_CONTEXT_KEY, new TextNode(Emojis.POLICE_EMOJI));
        } else if (isRemovedByAntiSpam(originalNode)) {
            originalNode.replace(Emojis.EXTRA_EMOJI_CONTEXT_KEY, new TextNode(Emojis.ROBOT_EMOJI));
        } else if (isRemovedByAntiEvilOps(originalNode)) {
            originalNode.replace(Emojis.EXTRA_EMOJI_CONTEXT_KEY, new TextNode(Emojis.DEVIL_EMOJI));
        } else if (isRemovedByCopyrightTakedown(originalNode)) {
            originalNode.replace(Emojis.EXTRA_EMOJI_CONTEXT_KEY, new TextNode(Emojis.COPYRIGHT_EMOJI));
        } else if (isRemovedByMod(originalNode)) {
            originalNode.replace(Emojis.EXTRA_EMOJI_CONTEXT_KEY, new TextNode(Emojis.BROOM_EMOJI));
        } else if (isDeletedByUser(originalNode)) {
            originalNode.replace(Emojis.EXTRA_EMOJI_CONTEXT_KEY, new TextNode(Emojis.TRASH_CAN_EMOJI));
        }
    }

    public static boolean isDeletedByUser(JsonNode content) {
        JsonNode selfText = content.get("selftext");
        if (selfText != null && selfText.asText().equals("[deleted]")) {
            return true;
        }
        JsonNode body = content.get("body");
        return body != null && body.asText().equals("[deleted]");
    }

    public static boolean isRemovedByMod(JsonNode content) {
        JsonNode removedByCategory = content.get("removed_by_category");
        if (removedByCategory != null && removedByCategory.asText().equals("moderator")) {
            return true;
        }
        JsonNode selfText = content.get("selftext");
        if (selfText != null && selfText.asText().equals("[removed]")) {
            return true;
        }
        JsonNode body = content.get("body");
        return body != null && body.asText().equals("[removed]");
    }

    public static boolean isRemovedByAntiSpam(JsonNode content) {
        JsonNode removedByCategory = content.get("removed_by_category");
        return removedByCategory != null && removedByCategory.asText().equals("reddit");
    }

    public static boolean isRemovedByAntiEvilOps(JsonNode content) {
        JsonNode removedByCategory = content.get("removed_by_category");
        return removedByCategory != null && removedByCategory.asText().equals("anti_evil_ops");
    }

    public static boolean isRemovedByAdmins(JsonNode content) {
        JsonNode removalReason = content.get("removal_reason");
        if (removalReason != null && removalReason.asText().equals("legal")) {
            return true;
        }
        JsonNode removedByCategory = content.get("removed_by_category");
        return removedByCategory != null && removedByCategory.asText().equals("content_takedown");
    }

    public static boolean isRemovedByCopyrightTakedown(JsonNode content) {
        JsonNode removedByCategory = content.get("removed_by_category");
        return removedByCategory != null && removedByCategory.asText().equals("copyright_takedown");
    }
}
