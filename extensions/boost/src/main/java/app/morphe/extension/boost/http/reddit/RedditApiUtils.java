/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boost.http.reddit;

import app.morphe.extension.vendored.com.fasterxml.jackson.databind.JsonNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.ArrayNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.JsonNodeFactory;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.NullNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.ObjectNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.TextNode;

import java.util.List;
import java.util.Optional;

import app.morphe.extension.shared.archive.ArchiveBadges;

public class RedditApiUtils {
    public static ObjectNode createListing(List<? extends JsonNode> children) {
        ArrayNode childrenNode = new ArrayNode(JsonNodeFactory.instance);
        for (JsonNode child : children) {
            childrenNode.add(child);
        }
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("kind", "Listing");
        ObjectNode data = root.putObject("data");
        data.putNull("after");
        data.putNull("dist");
        data.put("modhash", "");
        data.put("geo_filter", "");
        data.put("before", "");
        data.set("children", childrenNode);
        return root;
    }

    public static ObjectNode createListing(JsonNode originalListing, List<? extends JsonNode> children) {
        JsonNode originalData = originalListing.get("data");
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        List.of("after", "dist", "modhash", "geo_filter", "before").forEach(key -> {
            data.set(key, Optional.ofNullable(originalData.get(key)).orElse(NullNode.instance));
        });
        ArrayNode childrenNode = new ArrayNode(JsonNodeFactory.instance);
        for (JsonNode child : children) {
            childrenNode.add(child);
        }
        data.set("children", childrenNode);

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.set("kind", new TextNode("Listing"));
        root.set("data", data);
        return root;
    }

    static void setIfUnset(ObjectNode node, String key, JsonNode value) {
        if (!node.has(key)) node.set(key, value);
    }

    public static boolean isContentRemoved(JsonNode content) {
        return ArchiveBadges.isContentRemoved(content.path("removed_by_category").asText(),
                content.path("removal_reason").asText(), content.path("body").asText(), content.path("selftext").asText());
    }

    public static void setRemovalEmoji(ObjectNode originalNode) {
        String emoji = ArchiveBadges.removalReason(originalNode.path("removed_by_category").asText(),
                originalNode.path("removal_reason").asText(), originalNode.path("body").asText(),
                originalNode.path("selftext").asText()).emoji;
        if (emoji != null) originalNode.put(ArchiveRecoveryState.EXTRA_EMOJI_CONTEXT_KEY, emoji);
    }
}
