/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.http.reddit;

import app.morphe.extension.vendored.com.fasterxml.jackson.databind.JsonNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.ObjectNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.StringJoiner;

import app.morphe.extension.shared.archive.ArchiveBadges;

final class ArchiveRecoveryState {
    static final String RETRY_KEY = "morpheArchiveRetry";
    static final String LOADED_KEY = "morpheArchiveLoaded";
    static final String EXTRA_EMOJI_CONTEXT_KEY = "extraEmoji";

    private ArchiveRecoveryState() {}

    static String snapshot(Iterator<? extends Map.Entry<String, ?>> fields) {
        StringJoiner json = new StringJoiner(",", "{", "}");
        while (fields.hasNext()) {
            Map.Entry<String, ?> field = fields.next();
            if ("replies".equals(field.getKey()) || RETRY_KEY.equals(field.getKey())) continue;
            json.add(JsonNodeFactory.instance.textNode(field.getKey()) + ":" + String.valueOf(field.getValue()));
        }
        return json.toString();
    }

    static boolean isLoaded(JsonNode snapshot) {
        return snapshot.path(LOADED_KEY).asBoolean(false);
    }

    /** Live content gets only its text body replaced; removed content gets a full recovery. */
    static boolean isBodyOnly(JsonNode snapshot) {
        return !RedditApiUtils.isContentRemoved(snapshot)
                && !snapshot.path("removed_by_category").isTextual();
    }

    /** Marks data as loaded from the archive; shows a clock unless another icon already explains it. */
    static void loaded(ObjectNode data) {
        data.put(LOADED_KEY, true);
        if (data.path(EXTRA_EMOJI_CONTEXT_KEY).asText().isEmpty()) {
            data.put(EXTRA_EMOJI_CONTEXT_KEY, ArchiveBadges.ARCHIVED);
        }
    }

    static void failed(ObjectNode data) {
        data.put(EXTRA_EMOJI_CONTEXT_KEY, ArchiveBadges.WARNING);
    }
}
