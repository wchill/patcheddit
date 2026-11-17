/*
 * Copyright 2026 wchill.
 * Modified for Breal issue #196: fail-open recovery and compact comment caching (2026-09-11).
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 * Modified for Breal issue #219: batched comment recovery (2026-10-12).
 * Modified for the Breal undelete audit: live user state, negative caching, 403 reasons (2026-10-13).
 */

package app.morphe.extension.boost.http.reddit;

import static app.morphe.extension.boost.http.reddit.RedditApiUtils.setIfUnset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.boost.utils.LoggingUtils;
import app.morphe.extension.boost.utils.BoostUndeleteSettings;
import app.morphe.extension.boost.utils.MarkdownRenderer;
import app.morphe.extension.boost.http.arcticshift.ArcticShift;
import app.morphe.extension.boost.http.AutoSavingCache;
import app.morphe.extension.boost.http.HttpUtils;
import app.morphe.extension.boost.http.wayback.WaybackMachine;
import app.morphe.extension.boost.http.wayback.WaybackResponse;
import app.morphe.extension.boost.utils.Emojis;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class RedditSubmissionUndeleteInterceptor implements Interceptor {
    private static final Pattern SUBMISSION_API_REGEX = Pattern.compile("^https?://\\w+\\.reddit\\.com/comments/");
    private static final Pattern GALLERY_REGEX = Pattern.compile("window\\.___r\\s*=\\s*(\\{.+\\})\\s*</script>", Pattern.DOTALL | Pattern.MULTILINE);
    private static final String GALLERY_UNDELETE_MARKER = "morphe_boost_reddit_gallery_undelete_submission_json";
    private static final String FAIL_OPEN_MARKER = "morphe_boost_undelete_issue196_fail_open";
    private static final String COMMENT_BATCH_MARKER = "morphe_boost_undelete_issue219_comment_batch";
    private static final String POST_RECOVERY_FAIL_OPEN_MARKER = "morphe_boost_undelete_post_recovery_fail_open";
    private static final String[] COMMENT_RECOVERY_FIELDS = {
            "id", "author", "author_fullname", "author_flair_text", "body", "link_id"
    };
    // Per-user state from the live post; archived copies must not reset votes or saves.
    private static final String[] LIVE_STATE_FIELDS = {
            "saved", "likes", "clicked", "hidden", "visited", "archived", "locked", "stickied",
            "score", "ups", "upvote_ratio", "num_comments"
    };
    // 403 reasons that mean the content is unreachable rather than opt-in (quarantine) or auth.
    private static final Set<String> RECOVERABLE_403_REASONS = Set.of("private", "banned");
    private static final String MISSING_PREFIX = "missing:";
    // Arctic Shift ingests deletions with a delay, so a miss is only remembered for a while.
    private static final long MISSING_TTL_MILLIS = 6L * 60L * 60L * 1000L;
    private static final String NEGATIVE_CACHE_MARKER = "morphe_boost_undelete_negative_cache";

    private static final AutoSavingCache submissionCache = new AutoSavingCache("RedditSubmissions", 200);
    private static final AutoSavingCache commentsCache = new AutoSavingCache("RedditComments", 10000);

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!BoostUndeleteSettings.isRedditUndeleteEnabled()) {
            return chain.proceed(request);
        }

        String url = request.url().toString();

        if (!SUBMISSION_API_REGEX.matcher(url).find()) {
            return chain.proceed(request);
        }
        String[] pathParts = request.url().encodedPath().split("/");
        if (pathParts.length < 3) {
            return chain.proceed(request);
        }
        String submissionId = pathParts[2].replaceFirst("\\.json$", "");
        if (!submissionId.matches("[A-Za-z0-9]+")) {
            return chain.proceed(request);
        }

        // A cached post is enrichment, not evidence that the live thread is unavailable.
        // Always preserve Reddit's current comments and request context when available.
        Response response = chain.proceed(request);
        if (response.isSuccessful()) {
            if (response.body() == null) {
                return response;
            }
            // string() closes the original body. Keep the original text for recovery failures,
            // rather than returning a consumed body or issuing the Reddit request a second time.
            String originalJson = response.body().string();
            try {
                JsonNode data = handle200(response, submissionId, originalJson);
                return response.newBuilder()
                        .removeHeader("Content-Length")
                        .removeHeader("Content-Encoding")
                        .header("Content-Type", "application/json")
                        .body(HttpUtils.getResponseBodyFromJson(data))
                        .build();
            } catch (Throwable failure) {
                // Errors too: a linkage or stack failure in optional recovery must not crash Boost.
                logRecoveryFailure(failure);
                return response.newBuilder()
                        .removeHeader("Content-Length")
                        .body(ResponseBody.create(originalJson, response.body().contentType()))
                        .build();
            }
        }

        // Authentication, throttling and transient server errors are not deleted content.
        if (response.code() != 403 && response.code() != 404) {
            return response;
        }
        if (response.code() == 403 && !isRecoverable403(response)) {
            return response;
        }
        try {
            Response restored = HttpUtils.makeJsonResponse(request, handle4xx(request, submissionId));
            if (response.body() != null) {
                response.close();
            }
            return restored;
        } catch (Throwable failure) {
            logRecoveryFailure(failure);
            return response;
        }
    }

    /** Quarantined posts have an opt-in prompt and other 403s are auth problems; keep those. */
    private static boolean isRecoverable403(Response response) {
        try {
            JsonNode reason = HttpUtils.getJsonFromString(response.peekBody(16_384L).string()).get("reason");
            return reason != null && RECOVERABLE_403_REASONS.contains(reason.asText());
        } catch (Throwable failure) {
            return false;
        }
    }

    static String missingMarker() {
        return MISSING_PREFIX + System.currentTimeMillis();
    }

    /** True for a cache entry recording a recent archive miss. */
    static boolean isFreshMiss(String cached) {
        if (cached == null || !cached.startsWith(MISSING_PREFIX)) {
            return false;
        }
        try {
            long at = Long.parseLong(cached.substring(MISSING_PREFIX.length()));
            return System.currentTimeMillis() - at < MISSING_TTL_MILLIS;
        } catch (NumberFormatException failure) {
            return false;
        }
    }

    private static void logRecoveryFailure(Throwable failure) {
        LoggingUtils.logInfo(false, () -> FAIL_OPEN_MARKER + ": " + failure.getClass().getSimpleName());
    }

    private JsonNode handle200(Response redditResponse, String submissionId, String jsonStr) throws IOException {
        JsonNode json = HttpUtils.getJsonFromString(jsonStr);

        JsonNode submissionListing = json.get(0);
        JsonNode submission = submissionListing.get("data").get("children").get(0).get("data");
        if (RedditApiUtils.isContentRemoved(submission)) {
            try {
                JsonNode recovered = fetchDeletedSubmission(redditResponse.request(), submissionId, submission);
                submissionListing = RedditApiUtils.createListing(submissionListing, List.of(
                        new ObjectNode(JsonNodeFactory.instance,
                                new LinkedHashMap<>(Map.of("kind", new TextNode("t3"), "data", recovered)))));
            } catch (Throwable failure) {
                // Keep Reddit's post and still restore the comments below.
                ArchiveRecoveryState.failed((ObjectNode) submission);
                LoggingUtils.logInfo(false, () -> POST_RECOVERY_FAIL_OPEN_MARKER
                        + ": " + failure.getClass().getSimpleName());
            }
        }

        JsonNode commentsListing = json.get(1);
        restoreDeletedComments(commentsListing.get("data").get("children"));

        ArrayNode root = new ArrayNode(JsonNodeFactory.instance);
        root.add(submissionListing);
        root.add(commentsListing);
        return root;
    }

    private JsonNode handle4xx(Request request, String submissionId) throws IOException {
        ObjectNode submissionNode = new ObjectNode(JsonNodeFactory.instance, new LinkedHashMap<>(Map.of(
                "kind", new TextNode("t3"),
                "data", fetchDeletedSubmission(request, submissionId, null)
        )));
        ArrayNode comments = ArcticShift.getCommentTree(submissionId);
        List<JsonNode> topLevelReplies = new ArrayList<>();
        for (JsonNode node : comments) {
            ObjectNode reply = JsonNodeFactory.instance.objectNode();
            reply.setAll((ObjectNode) node.get("data"));
            checkReply(reply);
            topLevelReplies.add(new ObjectNode(JsonNodeFactory.instance, new LinkedHashMap<>(Map.of(
                    "kind", new TextNode("t1"),
                    "data", reply
            ))));
        }
        ArrayNode root = new ArrayNode(JsonNodeFactory.instance);
        ObjectNode submissionListing = RedditApiUtils.createListing(List.of(submissionNode));
        ObjectNode commentsListing = RedditApiUtils.createListing(topLevelReplies);
        root.add(submissionListing);
        root.add(commentsListing);
        return root;
    }

    private JsonNode fetchDeletedSubmission(Request request, String id, JsonNode dataNode) throws IOException {
        Map<String, JsonNode> liveState = captureLiveState(dataNode);
        JsonNode recoveredPost = recoverSubmission(request, id, dataNode);
        if (liveState.isEmpty() || recoveredPost == dataNode) {
            return recoveredPost;
        }
        // Cached copies keep archive defaults; the returned post keeps the user's live state.
        ObjectNode merged = JsonNodeFactory.instance.objectNode();
        merged.setAll((ObjectNode) recoveredPost);
        liveState.forEach(merged::set);
        return merged;
    }

    private static Map<String, JsonNode> captureLiveState(JsonNode dataNode) {
        Map<String, JsonNode> state = new HashMap<>();
        if (dataNode != null) {
            for (String key : LIVE_STATE_FIELDS) {
                JsonNode value = dataNode.get(key);
                if (value != null) {
                    state.put(key, value);
                }
            }
        }
        return state;
    }

    private JsonNode recoverSubmission(Request request, String id, JsonNode dataNode) throws IOException {
        Optional<String> cachedJson = submissionCache.get(id);
        if (cachedJson.isPresent() && isFreshMiss(cachedJson.get())) {
            LoggingUtils.logInfo(true, () -> NEGATIVE_CACHE_MARKER + ": post");
            if (dataNode != null) {
                return dataNode;
            }
            throw new IOException("Submission recently missing from archive");
        }
        if (cachedJson.isPresent()) {
            try {
                JsonNode cached = HttpUtils.getJsonFromString(cachedJson.get());
                if (isUsableSubmission(cached, id)) {
                    ObjectNode normalizedCached = JsonNodeFactory.instance.objectNode();
                    normalizedCached.setAll((ObjectNode) cached);
                    ArchiveRecoveryState.loaded(normalizedCached);
                    boolean changed = clearRecoveredUserDeletedRenderMarker(normalizedCached);
                    // Gallery metadata may have been unavailable when the post was cached.
                    changed |= restoreRedditGalleryMetadataSafely(request, id, normalizedCached);
                    if (changed) {
                        submissionCache.put(id, HttpUtils.getStringFromJson(normalizedCached));
                    }
                    LoggingUtils.logInfo(true, () -> "morphe_boost_undelete_issue196_post_restored_cached");
                    return normalizedCached;
                }
            } catch (RuntimeException failure) {
                logRecoveryFailure(failure);
            }
        }

        ArrayNode undeletedData = ArcticShift.getIds(ArcticShift.SubmissionType.POSTS, List.of(id));
        JsonNode recovered = null;
        if (undeletedData != null) {
            for (JsonNode candidate : undeletedData) {
                if (isUsableSubmission(candidate, id)) {
                    recovered = candidate;
                    break;
                }
            }
        }
        if (recovered == null) {
            submissionCache.put(id, missingMarker());
            if (dataNode != null) {
                return dataNode;
            }
            throw new IOException("No usable archived submission");
        }

        ObjectNode editableNode = recoveredSubmission(dataNode, recovered, id);
        restoreRedditGalleryMetadataSafely(request, id, editableNode);

        submissionCache.put(id, HttpUtils.getStringFromJson(editableNode));
        LoggingUtils.logInfo(true, () -> "morphe_boost_undelete_issue196_post_restored");
        return editableNode;
    }

    private static ObjectNode recoveredSubmission(JsonNode dataNode, JsonNode recovered, String id) {
        ObjectNode editableNode;
        if (dataNode == null) {
            editableNode = JsonNodeFactory.instance.objectNode();
            editableNode.set(Emojis.EXTRA_EMOJI_CONTEXT_KEY, new TextNode(Emojis.PROHIBITED_EMOJI));
        } else {
            // A copy: the live node supplies the user's state after recovery.
            editableNode = JsonNodeFactory.instance.objectNode();
            editableNode.setAll((ObjectNode) dataNode);
            RedditApiUtils.setRemovalEmoji(editableNode);
        }
        ArcticShift.updateSubmissionNode(editableNode, recovered);
        editableNode.remove(ArchiveRecoveryState.RETRY_KEY);
        ArchiveRecoveryState.loaded(editableNode);
        clearRecoveredUserDeletedRenderMarker(editableNode);
        editableNode.set("archived", BooleanNode.TRUE);
        editableNode.set("stickied", BooleanNode.FALSE);
        setIfUnset(editableNode, "locked", BooleanNode.TRUE);
        editableNode.set("saved", BooleanNode.FALSE);
        editableNode.set("clicked", BooleanNode.FALSE);
        editableNode.set("likes", NullNode.instance);
        setIfUnset(editableNode, "over_18", BooleanNode.FALSE);
        setIfUnset(editableNode, "is_self", BooleanNode.FALSE);
        setIfUnset(editableNode, "hidden", BooleanNode.FALSE);
        setIfUnset(editableNode, "name", new TextNode("t3_" + id));
        setIfUnset(editableNode, "suggested_sort", new TextNode("random"));
        setIfUnset(editableNode, "author_flair_css_class", NullNode.instance);
        setIfUnset(editableNode, "author_flair_text", NullNode.instance);
        setIfUnset(editableNode, "link_flair_css_class", NullNode.instance);
        setIfUnset(editableNode, "link_flair_text", NullNode.instance);

        return editableNode;
    }

    private static boolean clearRecoveredUserDeletedRenderMarker(ObjectNode node) {
        JsonNode removedByCategory = node == null ? null : node.get("removed_by_category");
        if (removedByCategory != null && "deleted".equals(removedByCategory.asText())) {
            node.set("removed_by_category", NullNode.instance);
            LoggingUtils.logInfo(true, () -> "morphe_boost_undelete_issue196_user_deleted_marker_cleared");
            return true;
        }
        return false;
    }

    private static boolean hasText(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.get(key);
        return value != null && value.isTextual() && !value.asText().isBlank();
    }

    private static boolean isUsableSubmission(JsonNode node, String id) {
        return node != null && node.getNodeType() == JsonNodeType.OBJECT
                && hasText(node, "id") && id.equals(node.get("id").asText())
                && hasText(node, "title") && hasText(node, "author")
                && hasText(node, "url") && hasText(node, "subreddit")
                && node.get("created_utc") != null && !node.get("created_utc").isNull();
    }

    private static JsonNode commentRecoveryFields(JsonNode node, String id) {
        if (node == null || node.getNodeType() != JsonNodeType.OBJECT || !hasText(node, "id")
                || !id.equals(node.get("id").asText()) || !hasText(node, "body")
                || RedditApiUtils.isContentRemoved(node)) {
            return null;
        }
        ObjectNode recovery = JsonNodeFactory.instance.objectNode();
        for (String key : COMMENT_RECOVERY_FIELDS) {
            JsonNode value = node.get(key);
            if (value != null && value.isTextual()) {
                recovery.set(key, value);
            }
        }
        return recovery;
    }

    /**
     * Restores removed comments in a comment, reply listing or array of comments.
     *
     * Archive lookups run inside Boost's own Reddit request, so all uncached IDs are
     * fetched in as few Arctic Shift calls as possible rather than one call per comment.
     */
    private void restoreDeletedComments(JsonNode root) {
        List<ObjectNode> removed = new ArrayList<>();
        collectRemovedComments(root, removed);
        if (removed.isEmpty()) {
            return;
        }

        Map<String, JsonNode> recoveredById = new HashMap<>();
        Set<String> uncached = new LinkedHashSet<>();
        int knownMissing = 0;
        for (ObjectNode comment : removed) {
            String commentId = comment.get("data").get("id").asText();
            if (recoveredById.containsKey(commentId) || uncached.contains(commentId)) {
                continue;
            }
            Optional<String> cachedJson = commentsCache.get(commentId);
            if (cachedJson.isPresent() && isFreshMiss(cachedJson.get())) {
                knownMissing++;
                continue;
            }
            JsonNode cached = cachedJson.isPresent() ? cachedCommentRecovery(commentId, cachedJson.get()) : null;
            if (cached != null) {
                recoveredById.put(commentId, cached);
            } else {
                uncached.add(commentId);
            }
        }
        Set<String> failedIds = new HashSet<>();
        fetchArchivedComments(new ArrayList<>(uncached), recoveredById, failedIds);

        int restored = 0;
        for (ObjectNode comment : removed) {
            ObjectNode data = (ObjectNode) comment.get("data");
            JsonNode recovered = recoveredById.get(data.get("id").asText());
            if (recovered == null) {
                if (failedIds.contains(data.get("id").asText())) {
                    ArchiveRecoveryState.failed(data);
                }
                continue;
            }
            try {
                // Merge into a new object so failed recovery cannot partially alter the live item.
                ObjectNode updated = JsonNodeFactory.instance.objectNode();
                updated.setAll(data);
                RedditApiUtils.setRemovalEmoji(updated);
                ArcticShift.updateCommentNode(updated, recovered);
                ArchiveRecoveryState.loaded(updated);
                JsonNode collapsedReason = data.get("collapsed_reason_code");
                if (collapsedReason != null && "DELETED".equals(collapsedReason.asText())) {
                    updated.replace("collapsed", BooleanNode.FALSE);
                }
                comment.replace("data", updated);
                restored++;
            } catch (RuntimeException failure) {
                ArchiveRecoveryState.failed(data);
                logRecoveryFailure(failure);
            }
        }

        final int removedCount = removed.size();
        final int uncachedCount = uncached.size();
        final int knownMissingCount = knownMissing;
        final int restoredCount = restored;
        LoggingUtils.logInfo(true, () -> COMMENT_BATCH_MARKER
                + ": removed=" + removedCount
                + " uncached=" + uncachedCount
                + " knownMissing=" + knownMissingCount
                + " restored=" + restoredCount);
    }

    private void collectRemovedComments(JsonNode node, List<ObjectNode> removed) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectRemovedComments(child, removed);
            }
            return;
        }
        if (node.getNodeType() != JsonNodeType.OBJECT || !hasText(node, "kind")
                || !"t1".equals(node.get("kind").asText())
                || !(node.get("data") instanceof ObjectNode)) {
            return;
        }
        ObjectNode data = (ObjectNode) node.get("data");
        if (RedditApiUtils.isContentRemoved(data) && hasText(data, "id")) {
            removed.add((ObjectNode) node);
        }

        JsonNode children = getNested(data.get("replies"), "data", "children");
        if (children != null && children.isArray()) {
            collectRemovedComments(children, removed);
        }
    }

    private JsonNode cachedCommentRecovery(String commentId, String cachedJson) {
        if (cachedJson.startsWith(MISSING_PREFIX)) {
            return null; // Expired miss; ask the archive again.
        }
        try {
            JsonNode recovered = commentRecoveryFields(HttpUtils.getJsonFromString(cachedJson), commentId);
            if (recovered != null) {
                // Rewriting hits also projects older, full-comment cache entries down to these fields.
                commentsCache.put(commentId, HttpUtils.getStringFromJson(recovered));
            }
            return recovered;
        } catch (RuntimeException failure) {
            logRecoveryFailure(failure);
            return null;
        }
    }

    private void fetchArchivedComments(List<String> commentIds, Map<String, JsonNode> recoveredById,
                                       Set<String> failedIds) {
        for (int from = 0; from < commentIds.size(); from += ArcticShift.MAX_IDS_PER_REQUEST) {
            List<String> batch = commentIds.subList(
                    from, Math.min(from + ArcticShift.MAX_IDS_PER_REQUEST, commentIds.size()));
            try {
                ArrayNode response = ArcticShift.getIds(ArcticShift.SubmissionType.COMMENTS, batch);
                Set<String> missing = new HashSet<>(batch);
                for (JsonNode candidate : response) {
                    if (!hasText(candidate, "id")) {
                        continue;
                    }
                    String commentId = candidate.get("id").asText();
                    if (!batch.contains(commentId) || recoveredById.containsKey(commentId)) {
                        continue;
                    }
                    JsonNode recovered = commentRecoveryFields(candidate, commentId);
                    if (recovered != null) {
                        recoveredById.put(commentId, recovered);
                        missing.remove(commentId);
                        // Do not retain the live replies subtree in every cached ancestor comment.
                        commentsCache.put(commentId, HttpUtils.getStringFromJson(recovered));
                    }
                }
                // Only a successful lookup proves absence; failures below are not cached.
                String marker = missingMarker();
                for (String commentId : missing) {
                    commentsCache.put(commentId, marker);
                }
            } catch (IOException | RuntimeException failure) {
                // Keep what earlier batches recovered; the rest of the thread stays live.
                failedIds.addAll(commentIds.subList(from, commentIds.size()));
                logRecoveryFailure(failure);
                return;
            }
        }
    }

    static final class ReloadResult {
        final ObjectNode data;
        final boolean found;

        ReloadResult(ObjectNode data, boolean found) {
            this.data = data;
            this.found = found;
        }
    }

    static ReloadResult reload(ObjectNode original) throws IOException {
        boolean comment = original.has("body");
        String id = original.path("id").asText();
        ArrayNode response = ArcticShift.getIds(comment ? ArcticShift.SubmissionType.COMMENTS
                : ArcticShift.SubmissionType.POSTS, List.of(id), true);
        ObjectNode updated = original.deepCopy();
        updated.remove(ArchiveRecoveryState.RETRY_KEY);
        boolean bodyOnly = ArchiveRecoveryState.isBodyOnly(original);
        if (!bodyOnly) updated.remove(Emojis.EXTRA_EMOJI_CONTEXT_KEY);
        String bodyKey = comment ? "body" : "selftext";
        for (JsonNode candidate : response) {
            JsonNode recovered = comment ? commentRecoveryFields(candidate, id)
                    : isUsableSubmission(candidate, id) ? candidate : null;
            if (recovered == null) continue;
            if (bodyOnly) {
                JsonNode body = recovered.get(bodyKey);
                if (body == null || !body.isTextual() || RedditApiUtils.isContentRemoved(recovered)) continue;
                updated.set(bodyKey, body);
                updated.put(bodyKey + "_html", MarkdownRenderer.render(body.asText()));
                ArchiveRecoveryState.loaded(updated);
                if (comment) commentsCache.put(id, HttpUtils.getStringFromJson(recovered));
                return new ReloadResult(updated, true);
            }
            RedditApiUtils.setRemovalEmoji(updated);
            if (comment) {
                ArcticShift.updateCommentNode(updated, recovered);
                if ("DELETED".equals(updated.path("collapsed_reason_code").asText())) {
                    updated.put("collapsed", false);
                }
                ArchiveRecoveryState.loaded(updated);
                commentsCache.put(id, HttpUtils.getStringFromJson(recovered));
            } else {
                updated = recoveredSubmission(updated, recovered, id);
                submissionCache.put(id, HttpUtils.getStringFromJson(updated));
                for (Map.Entry<String, JsonNode> entry : captureLiveState(original).entrySet()) {
                    updated.set(entry.getKey(), entry.getValue());
                }
            }
            return new ReloadResult(updated, true);
        }
        (comment ? commentsCache : submissionCache).put(id, missingMarker());
        return new ReloadResult(updated, false);
    }

    /**
     * Fills fields Boost expects on archived comments, for a comment and all its replies.
     * Iterative, so very deep archived threads cannot overflow the stack.
     */
    private void checkReply(ObjectNode root) {
        ArrayDeque<ObjectNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            ObjectNode node = pending.pop();
            ArchiveRecoveryState.loaded(node);
            setIfUnset(node, "saved", BooleanNode.FALSE);
            setIfUnset(node, "controversiality", new IntNode(0));
            setIfUnset(node, "score_hidden", BooleanNode.FALSE);
            setIfUnset(node, "locked", BooleanNode.TRUE);
            node.set("likes", NullNode.instance);

            if (node.get("body") != null) {
                String renderedHtml = MarkdownRenderer.render(node.get("body").asText());
                node.set("body_html", new TextNode(renderedHtml));
            }

            JsonNode replies = node.get("replies");
            if (replies == null) {
                node.set("replies", new TextNode(""));
            } else if (!replies.isNull() && replies.getNodeType() == JsonNodeType.OBJECT
                    && replies.get("data") != null && replies.get("data").get("children") != null) {
                ObjectNode newReplies = JsonNodeFactory.instance.objectNode();
                newReplies.setAll((ObjectNode) replies);
                ObjectNode dataNode = JsonNodeFactory.instance.objectNode();
                dataNode.setAll((ObjectNode) replies.get("data"));
                ArrayNode newReplyArray = new ArrayNode(JsonNodeFactory.instance);
                for (JsonNode child : dataNode.get("children")) {
                    ObjectNode newChild = JsonNodeFactory.instance.objectNode();
                    newChild.setAll((ObjectNode) child);
                    ObjectNode newChildData = JsonNodeFactory.instance.objectNode();
                    newChildData.setAll((ObjectNode) newChild.get("data"));
                    newChild.set("data", newChildData);
                    newReplyArray.add(newChild);
                    pending.push(newChildData);
                }
                dataNode.set("children", newReplyArray);
                newReplies.set("data", dataNode);
                node.set("replies", newReplies);
            }
        }
    }

    /**
     * Restores gallery metadata from the Wayback Machine. The text post is usable without it,
     * so archive failures are logged rather than discarding the recovered post.
     * Returns true when metadata was added.
     */
    private boolean restoreRedditGalleryMetadataSafely(Request request, String id, ObjectNode editableNode) {
        if (!isRedditGallery(editableNode.get("url"))) {
            return false;
        }

        if (hasUsableGalleryMetadata(editableNode)) {
            editableNode.set("is_gallery", BooleanNode.TRUE);
            LoggingUtils.logInfo(true, () -> GALLERY_UNDELETE_MARKER + ": existing gallery metadata for " + id);
            return false;
        }

        try {
            return restoreRedditGalleryMetadata(request, id, editableNode);
        } catch (Throwable failure) {
            LoggingUtils.logInfo(false, () -> GALLERY_UNDELETE_MARKER + ": failed for " + id
                    + ": " + failure.getClass().getSimpleName());
            return false;
        }
    }

    private boolean restoreRedditGalleryMetadata(Request request, String id, ObjectNode editableNode) throws IOException {
        String galleryUrl = editableNode.get("url").asText();
        JsonNode data = getPostDataFromWayback(request, galleryUrl);
        JsonNode media = getNested(data, "posts", "models", "t3_" + id, "media");
        if (media == null || media.isNull()) {
            return false;
        }

        JsonNode gallery = media.get("gallery");
        JsonNode mediaMetadata = media.get("mediaMetadata");
        if (!isUsableJsonNode(gallery) || !isUsableJsonNode(mediaMetadata)) {
            return false;
        }

        editableNode.set("gallery_data", gallery);
        editableNode.set("media_metadata", mediaMetadata);
        editableNode.set("is_gallery", BooleanNode.TRUE);
        LoggingUtils.logInfo(true, () -> GALLERY_UNDELETE_MARKER + ": restored gallery metadata for " + id);
        return true;
    }

    private boolean hasUsableGalleryMetadata(JsonNode node) {
        return node != null
                && isUsableJsonNode(node.get("gallery_data"))
                && isUsableJsonNode(node.get("media_metadata"));
    }

    private boolean isUsableJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isTextual() && node.asText().isBlank()) {
            return false;
        }
        return !(node.getNodeType() == JsonNodeType.OBJECT || node.getNodeType() == JsonNodeType.ARRAY) || node.size() > 0;
    }

    private JsonNode getNested(JsonNode node, String... path) {
        JsonNode current = node;
        for (String segment : path) {
            if (current == null || current.isNull()) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private JsonNode getPostDataFromWayback(Request request, String galleryUrl) throws IOException {
        String html;
        try (WaybackResponse response = WaybackMachine.getFromWayback(request, galleryUrl)) {
            if (!response.found() || response.getResponse().body() == null) {
                return null;
            }
            html = response.getResponse().body().string();
        }
        Matcher matcher = GALLERY_REGEX.matcher(html);
        if (matcher.find()) {
            String json = matcher.group(1);
            json = json.replaceAll("(web\\.archive\\.org/web/\\d+)/", "$1if_/");
            return HttpUtils.getJsonFromString(json);
        }
        return null;
    }

    private boolean isRedditGallery(JsonNode urlNode) {
        if (urlNode == null || urlNode.isNull()) return false;
        String url = urlNode.asText();
        return url != null && url.contains("reddit.com/gallery/");
    }

    private boolean hasImage(String url) {
        if (url == null) return false;
        if (url.isBlank()) return false;
        return url.endsWith(".jpg") || url.endsWith(".png") || url.endsWith(".webp") || url.endsWith(".gif") || url.endsWith(".jpeg");
    }
}
