/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 * Modified for Breal undelete audit: live-first, fail-open recovery (2026-10-12).
 */

package app.morphe.extension.boost.http.reddit;

import static app.morphe.extension.boost.http.reddit.RedditApiUtils.setIfUnset;

import app.morphe.extension.vendored.com.fasterxml.jackson.databind.JsonNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.ArrayNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.BooleanNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.IntNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.JsonNodeFactory;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.NullNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.ObjectNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.node.TextNode;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.boost.http.arcticshift.ArcticShift;
import app.morphe.extension.boost.utils.BoostUndeleteSettings;
import app.morphe.extension.shared.archive.ArchiveBadges;
import app.morphe.extension.shared.archive.HttpUtils;
import app.morphe.extension.shared.cache.AutoSavingCache;
import app.morphe.extension.shared.debug.LoggingUtils;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RedditSubredditUndeleteInterceptor implements Interceptor {
    private static final Pattern SUBREDDIT_ABOUT_API_REGEX = Pattern.compile("^https?://\\w+\\.reddit\\.com/r/(\\w+)/about$");
    private static final Pattern SUBREDDIT_ABOUT_RULES_API_REGEX = Pattern.compile("^https?://\\w+\\.reddit\\.com/r/(\\w+)/about/rules$");
    private static final Pattern SUBREDDIT_POSTS_API_REGEX = Pattern.compile("^https?://\\w+\\.reddit\\.com/r/(\\w+)/(?:hot|new|rising|top|controversial)");
    private static final String SUBREDDIT_LISTING_SCOPE_MARKER = "morphe_boost_reddit_subreddit_listing_fallback_scope";
    private static final String FAIL_OPEN_MARKER = "morphe_boost_undelete_subreddit_fail_open";
    private final AutoSavingCache subredditCache = new AutoSavingCache("RedditSubreddits", 10);
    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!BoostUndeleteSettings.isRedditUndeleteEnabled()) {
            return chain.proceed(request);
        }

        String url = request.url().toString();

        Matcher matcher = SUBREDDIT_ABOUT_API_REGEX.matcher(url);
        if (matcher.find()) {
            return handleAbout(chain, request, matcher.group(1));
        }

        matcher = SUBREDDIT_ABOUT_RULES_API_REGEX.matcher(url);
        if (matcher.find()) {
            return handleRules(chain, request, matcher.group(1));
        }

        matcher = SUBREDDIT_POSTS_API_REGEX.matcher(url);
        if (matcher.find()) {
            return handlePosts(chain, request, matcher.group(1));
        }

        return chain.proceed(request);
    }

    // Reddit answers banned and private subreddits with 404 and 403. Anything else (auth,
    // rate limits, server errors) is not removal, and an archive copy would hide the real state.
    private static boolean isRemovalStatus(Response response) {
        return response.code() == HttpURLConnection.HTTP_NOT_FOUND
                || response.code() == HttpURLConnection.HTTP_FORBIDDEN;
    }

    private static void logRecoveryFailure(String subredditName, Throwable failure) {
        LoggingUtils.logInfo(false, () -> FAIL_OPEN_MARKER + ": " + subredditName
                + " " + failure.getClass().getSimpleName());
    }

    private Response handleAbout(Chain chain, Request request, String subredditName) throws IOException {
        // Reddit stays authoritative: the archive is only consulted after a removal status.
        Response apiResponse = chain.proceed(request);
        if (apiResponse.isSuccessful() || !isRemovalStatus(apiResponse)) {
            return apiResponse;
        }

        try {
            Optional<String> cached = subredditCache.get(subredditName);
            String subredditJson;
            if (cached.isPresent()) {
                subredditJson = cached.get();
            } else {
                // Sometimes subreddits don't show up if searching by name, so attempt to get a post and retrieve the sub id from that.
                JsonNode samplePost = ArcticShift.getSubredditPosts(subredditName, 1).get(0);
                if (samplePost == null || samplePost.get("subreddit_id") == null) {
                    return apiResponse;
                }
                String subredditId = samplePost.get("subreddit_id").asText();
                subredditJson = HttpUtils.getStringFromJson(ArcticShift.getSubredditInfoById(subredditId));
                subredditCache.put(subredditName, subredditJson);
            }
            Response restored = HttpUtils.makeJsonResponse(request, subredditJson);
            apiResponse.close();
            return restored;
        } catch (Throwable failure) {
            // Optional archive recovery must never replace Reddit's own answer with a crash.
            logRecoveryFailure(subredditName, failure);
            return apiResponse;
        }
    }

    private Response handleRules(Chain chain, Request request, String subredditName) throws IOException {
        Response response = chain.proceed(request);
        if (response.isSuccessful() || !isRemovalStatus(response)) {
            return response;
        }
        response.close();

        ObjectNode rulesNode = new ObjectNode(
                JsonNodeFactory.instance, new LinkedHashMap<>(Map.of(
                        "rules", new ArrayNode(JsonNodeFactory.instance),
                        "site_rules", new ArrayNode(JsonNodeFactory.instance),
                        "site_rules_flow", new ArrayNode(JsonNodeFactory.instance)
                ))
        );
        return HttpUtils.makeJsonResponse(request, rulesNode);
    }

    private Response handlePosts(Chain chain, Request request, String subredditName) throws IOException {
        Response response = chain.proceed(request);
        // Keep normal successful listings and non-removal failures untouched.
        if (response.isSuccessful() || !isRemovalStatus(response)) {
            return response;
        }

        // r/all and r/popular are global listing surfaces, not deleted/banned subreddit detail surfaces.
        // Falling back to Arctic Shift here can produce an empty synthetic listing, especially for r/all.
        if (isGlobalListingSubreddit(subredditName)) {
            final int statusCode = response.code();
            LoggingUtils.logInfo(true, () -> SUBREDDIT_LISTING_SCOPE_MARKER
                    + ": pass through global listing "
                    + subredditName
                    + " after HTTP "
                    + statusCode);
            return response;
        }

        try {
            Response restored = archivedListing(request, subredditName, response.code());
            if (restored == null) {
                return response;
            }
            response.close();
            return restored;
        } catch (Throwable failure) {
            logRecoveryFailure(subredditName, failure);
            return response;
        }
    }

    /** Returns an Arctic Shift listing, or null when the archive has nothing usable. */
    private Response archivedListing(Request request, String subredditName, int statusCode) throws IOException {
        HttpUrl url = request.url();

        // Arctic Shift accepts 1..100; Boost asks for up to 100 but other clients may ask for more.
        Integer limit = Optional.ofNullable(url.queryParameter("limit"))
                .map(Integer::parseInt)
                .map(value -> Math.max(1, Math.min(100, value)))
                .orElse(null);
        // Reddit's before/after are page cursors; this listing's cursors are post dates.
        String before = url.queryParameter("before");
        String after = url.queryParameter("after");
        String t = url.queryParameter("t");
        if (t != null && !"all".equals(t) && before == null) {
            LocalDateTime afterDateTime = LocalDateTime.now(ZoneOffset.UTC);
            if ("hour".equals(t)) {
                afterDateTime = afterDateTime.minusHours(1);
            } else if ("day".equals(t)) {
                afterDateTime = afterDateTime.minusDays(1);
            } else if ("week".equals(t)) {
                afterDateTime = afterDateTime.minusWeeks(1);
            } else if ("month".equals(t)) {
                afterDateTime = afterDateTime.minusMonths(1);
            } else if ("year".equals(t)) {
                afterDateTime = afterDateTime.minusYears(1);
            } else {
                afterDateTime = LocalDateTime.ofEpochSecond(0L, 0, ZoneOffset.UTC);
            }
            // The time window is a lower bound on post dates. It must not replace the "after"
            // cursor, or every page of a top/controversial listing repeats the first one.
            before = afterDateTime.toLocalDate() + "T" + afterDateTime.toLocalTime();
        }

        List<JsonNode> children = new ArrayList<>();
        JsonNode rootNode = ArcticShift.getSubredditPosts(subredditName, limit, before, after);
        if (rootNode == null || !rootNode.isArray() || rootNode.size() == 0) {
            LoggingUtils.logInfo(true, () -> SUBREDDIT_LISTING_SCOPE_MARKER
                    + ": empty Arctic Shift fallback for "
                    + subredditName
                    + ", pass through original HTTP "
                    + statusCode);
            return null;
        }

        long earliestTimestamp = 365241780471L;
        long latestTimestamp = 0L;
        for (JsonNode node : rootNode) {
            ObjectNode child = JsonNodeFactory.instance.objectNode();
            child.setAll((ObjectNode) node);
            child.put(ArchiveRecoveryState.EXTRA_EMOJI_CONTEXT_KEY, ArchiveBadges.UNAVAILABLE);
            child.set("saved", BooleanNode.FALSE);
            child.set("likes", NullNode.instance);
            applyArchivedPostDefaults(child, subredditName);

            children.add(new ObjectNode(JsonNodeFactory.instance, new LinkedHashMap<>(Map.of(
                    "kind", new TextNode("t3"),
                    "data", child
            ))));

            Long timestamp = parseCreatedUtcEpochSeconds(child.get("created_utc"));
            if (timestamp == null) {
                continue;
            }
            if (timestamp < earliestTimestamp) {
                earliestTimestamp = timestamp;
            }
            if (timestamp > latestTimestamp) {
                latestTimestamp = timestamp;
            }
        }

        if (children.isEmpty()) {
            LoggingUtils.logInfo(true, () -> SUBREDDIT_LISTING_SCOPE_MARKER
                    + ": no usable fallback children for "
                    + subredditName
                    + ", pass through original HTTP "
                    + statusCode);
            return null;
        }

        ObjectNode listing = RedditApiUtils.createListing(children);
        ObjectNode listingData = (ObjectNode) listing.get("data");
        listingData.replace("after", new TextNode(convertUnixTimestamp(earliestTimestamp)));
        listingData.replace("before", new TextNode(convertUnixTimestamp(latestTimestamp)));
        return HttpUtils.makeJsonResponse(request, listing);
    }

    /**
     * Older archived posts (Pushshift-era) lack fields current Reddit listings always have.
     * Boost drops listing children without them, so a restored subreddit looked empty.
     */
    static void applyArchivedPostDefaults(ObjectNode post, String subredditName) {
        setIfUnset(post, "archived", BooleanNode.TRUE);
        setIfUnset(post, "locked", BooleanNode.TRUE);
        setIfUnset(post, "hidden", BooleanNode.FALSE);
        setIfUnset(post, "clicked", BooleanNode.FALSE);
        setIfUnset(post, "visited", BooleanNode.FALSE);
        setIfUnset(post, "stickied", BooleanNode.FALSE);
        setIfUnset(post, "spoiler", BooleanNode.FALSE);
        setIfUnset(post, "over_18", BooleanNode.FALSE);
        setIfUnset(post, "is_self", BooleanNode.FALSE);
        setIfUnset(post, "score", new IntNode(0));
        setIfUnset(post, "num_comments", new IntNode(0));
        setIfUnset(post, "subreddit", new TextNode(subredditName));
        setIfUnset(post, "subreddit_name_prefixed", new TextNode("r/" + subredditName));
        JsonNode id = post.get("id");
        if (id != null && id.isTextual()) {
            setIfUnset(post, "name", new TextNode("t3_" + id.asText()));
        }
        JsonNode created = post.get("created_utc");
        if (created != null) {
            setIfUnset(post, "created", created);
        }
    }

    private static Long parseCreatedUtcEpochSeconds(JsonNode node) {
        if (node == null) {
            return null;
        }

        try {
            String value = node.asText();
            if (value == null) {
                return null;
            }

            value = value.trim();
            if (value.isEmpty()) {
                return null;
            }

            int decimalIndex = value.indexOf('.');
            if (decimalIndex > 0) {
                value = value.substring(0, decimalIndex);
            }

            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isGlobalListingSubreddit(String subredditName) {
        if (subredditName == null) {
            return false;
        }

        return "all".equalsIgnoreCase(subredditName)
                || "popular".equalsIgnoreCase(subredditName);
    }

    private static String convertUnixTimestamp(long epochTime) {
        LocalDateTime ldt = LocalDateTime.ofEpochSecond(epochTime, 0, ZoneOffset.UTC);
        return ldt.toLocalDate() + "T" + ldt.toLocalTime();
    }
}
