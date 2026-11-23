/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 * Modified for Breal issue #219: bounded calls and failure cooldown (2026-10-12).
 */

package app.morphe.extension.shared.archive.arcticshift;

import app.morphe.extension.vendored.com.fasterxml.jackson.databind.JsonNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.ArrayNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.JsonNodeFactory;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.ObjectNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import app.morphe.extension.shared.archive.HttpUtils;
import app.morphe.extension.shared.debug.LoggingUtils;

public class ArcticShift {
    public enum SubmissionType {
        POSTS,
        COMMENTS
    }

    private static final String ARCTIC_SHIFT_NULL_DATA_MARKER = "morphe_boost_arcticshift_null_data_fail_open";
    private static final String ARCTIC_SHIFT_COOLDOWN_MARKER = "morphe_boost_arcticshift_issue219_cooldown";

    /** Arctic Shift rejects ID lookups with more than 500 IDs. */
    public static final int MAX_IDS_PER_REQUEST = 500;

    static final int COMMENT_TREE_LIMIT = 3000;

    private static final long QUERY_TIMEOUT_RETRY_DELAY_MS = 1_000L;

    // Lookups run inside Reddit requests, so a slow archive must not hold them for long.
    private static final long CALL_TIMEOUT_MS = 10_000L;
    private static final long COMMENT_TREE_CALL_TIMEOUT_MS = 20_000L;

    // After a failure, skip lookups briefly instead of making every thread wait for the same outage.
    private static final long FAILURE_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static volatile Long lastFailureNanos;

    private static final String[] POSTS_FIELDS = {
            "id", "title", "author", "author_fullname", "author_flair_text", "crosspost_parent",
            "link_flair_text", "selftext", "url", "preview", "gallery_data", "media_metadata", "post_hint", "domain",
            "is_reddit_media_domain", "is_self", "is_video", "is_gallery", "media", "media_embed",
            "thumbnail"
    };

    private static final String[] COMMENTS_FIELDS = {
            "id", "author", "author_fullname", "author_flair_text", "body", "link_id"
    };

    private static JsonNode makeApiCall(String url) throws IOException {
        return makeApiCall(url, CALL_TIMEOUT_MS);
    }

    private static JsonNode makeApiCall(String url, long callTimeoutMillis) throws IOException {
        return makeApiCall(url, callTimeoutMillis, false);
    }

    private static JsonNode makeApiCall(String url, long callTimeoutMillis, boolean retry) throws IOException {
        Long failedAt = lastFailureNanos;
        if (!retry && failedAt != null && System.nanoTime() - failedAt < FAILURE_COOLDOWN_NANOS) {
            throw new IOException(ARCTIC_SHIFT_COOLDOWN_MARKER + ": skipping lookup after recent failure");
        }
        LoggingUtils.logInfo(true, () -> String.format("Arctic Shift: %s", url));
        try {
            JsonNode result = HttpUtils.getJson(url, callTimeoutMillis, retry);
            lastFailureNanos = null;
            return result;
        } catch (HttpUtils.HttpStatusException failure) {
            // A query-level timeout comes back as HTTP 422 with an error body. The service is up,
            // so hand the body to the caller (which may retry) instead of starting a cooldown.
            if (failure.code == 422 && failure.body.contains("\"error\"")) {
                try {
                    return HttpUtils.getJsonFromString(failure.body);
                } catch (RuntimeException ignored) {
                    // Not JSON after all; treat as a failure below.
                }
            }
            lastFailureNanos = System.nanoTime();
            LoggingUtils.logInfo(false, () -> ARCTIC_SHIFT_COOLDOWN_MARKER
                    + ": HTTP " + failure.code);
            throw failure;
        } catch (IOException failure) {
            lastFailureNanos = System.nanoTime();
            LoggingUtils.logInfo(false, () -> ARCTIC_SHIFT_COOLDOWN_MARKER
                    + ": " + failure.getClass().getSimpleName());
            throw failure;
        }
    }

    public static ArrayNode getIds(SubmissionType submissionType, Collection<String> ids) throws IOException {
        return getIds(submissionType, ids, false);
    }

    public static ArrayNode getIds(SubmissionType submissionType, Collection<String> ids, boolean retry) throws IOException {
        if (ids.size() > MAX_IDS_PER_REQUEST) {
            throw new IllegalArgumentException("At most " + MAX_IDS_PER_REQUEST + " IDs per request");
        }
        String url = ArcticShiftSettings.getBaseUrl() + submissionType.toString().toLowerCase(java.util.Locale.ROOT)
                + "/ids?ids=" + String.join(",", ids);
        return idsData(makeApiCall(url, CALL_TIMEOUT_MS, retry));
    }

    static ArrayNode idsData(JsonNode response) throws IOException {
        JsonNode data = response == null ? null : response.get("data");
        // Callers cache an empty answer as "not archived", so an error payload must not look empty.
        if (data == null || !data.isArray() || response.hasNonNull("error")) {
            throw new IOException(ARCTIC_SHIFT_NULL_DATA_MARKER + ": non-array data for ids lookup");
        }
        return (ArrayNode) data;
    }

    public static ArrayNode getCommentTree(String linkId) throws IOException {
        // A limit of 25000 made deleted megathreads time out.
        String url = ArcticShiftSettings.getBaseUrl() + "comments/tree?limit=" + COMMENT_TREE_LIMIT + "&link_id=" + linkId;
        return getArrayDataOrEmpty(makeApiCall(url, COMMENT_TREE_CALL_TIMEOUT_MS), "comments/tree");
    }

    public static JsonNode getSubredditInfoById(String id) throws IOException {
        String url = ArcticShiftSettings.getBaseUrl() + "subreddits/ids?ids=" + id;
        JsonNode data = makeApiCall(url);
        return new ObjectNode(
                JsonNodeFactory.instance, new LinkedHashMap<>(Map.of(
                        "kind", new TextNode("t5"),
                        "data", data.get("data").get(0)
                ))
        );
    }

    public static ArrayNode getSubredditPosts(String subredditName, Integer limit) throws IOException {
        return getSubredditPosts(subredditName, limit, null, null);
    }

    public static ArrayNode getSubredditPosts(String subredditName, Integer limit, String before, String after) throws IOException {
        String url = ArcticShiftSettings.getBaseUrl() + "posts/search?sort=desc&subreddit=" + subredditName + "&limit=";
        if (limit == null) {
            url += "auto";
        } else if (limit > 0 && limit <= 100) {
            url += limit;
        } else {
            throw new IllegalArgumentException("Limit must either be null or between 1 and 100 inclusive");
        }

        if (before != null) {
            url += "&after=" + before;
        }

        if (after != null) {
            url += "&before=" + after;
        }

        JsonNode result = makeApiCall(url);
        if (isQueryTimeout(result)) {
            // Cold subreddit searches often time out once on Arctic Shift's side and then succeed.
            try {
                Thread.sleep(QUERY_TIMEOUT_RETRY_DELAY_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while retrying Arctic Shift search");
            }
            result = makeApiCall(url);
        }
        return getArrayDataOrEmpty(result, "posts/search");
    }

    private static boolean isQueryTimeout(JsonNode result) {
        JsonNode data = result == null ? null : result.get("data");
        JsonNode error = result == null ? null : result.get("error");
        return (data == null || data.isNull()) && error != null && error.asText().startsWith("Timeout");
    }

    private static ArrayNode getArrayDataOrEmpty(JsonNode rootNode, String context) {
        JsonNode dataNode = rootNode == null ? null : rootNode.get("data");
        if (dataNode != null && dataNode.isArray()) {
            return (ArrayNode) dataNode;
        }
        LoggingUtils.logInfo(true, () -> ARCTIC_SHIFT_NULL_DATA_MARKER
                + ": non-array data for "
                + context
                + ", skipping Arctic Shift fallback");
        return new ArrayNode(app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
    }

    public static void updateSubmissionNode(ObjectNode oldNode, JsonNode newNode, UnaryOperator<String> renderMarkdown) {
        mergeJsonNodes(oldNode, newNode, POSTS_FIELDS, "selftext", renderMarkdown);
    }

    public static void updateCommentNode(ObjectNode oldNode, JsonNode newNode, UnaryOperator<String> renderMarkdown) {
        mergeJsonNodes(oldNode, newNode, COMMENTS_FIELDS, "body", renderMarkdown);
    }

    private static void mergeJsonNodes(ObjectNode oldNode, JsonNode newNode, String[] fields, String markdownField,
                                       UnaryOperator<String> renderMarkdown) {
        if (oldNode == null) {
            throw new IllegalArgumentException("oldNode must not be null");
        }

        if (newNode != null) {
            for (Iterator<String> it = newNode.fieldNames(); it.hasNext(); ) {
                String fieldName = it.next();
                if (oldNode.get(fieldName) == null) {
                    oldNode.replace(fieldName, newNode.get(fieldName));
                }
            }

            for (String fieldName : fields) {
                JsonNode node = newNode.get(fieldName);
                if (node != null) {
                    oldNode.replace(fieldName, node);
                    if (fieldName.equals(markdownField)) {
                        String renderedHtml = renderMarkdown.apply(node.asText());
                        oldNode.replace(markdownField + "_html", new TextNode(renderedHtml));
                    }
                }
            }
        }
    }
}
