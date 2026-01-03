package app.morphe.extension.boostforreddit.http.reddit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.boostforreddit.utils.EditableObjectNode;
import app.morphe.extension.boostforreddit.http.arcticshift.ArcticShift;
import app.morphe.extension.boostforreddit.http.AutoSavingCache;
import app.morphe.extension.boostforreddit.http.HttpUtils;
import app.morphe.extension.boostforreddit.utils.Emojis;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RedditSubredditUndeleteInterceptor implements Interceptor {
    private static final Pattern SUBREDDIT_ABOUT_API_REGEX = Pattern.compile("^https?://\\w+\\.reddit\\.com/r/(\\w+)/about$");
    private static final Pattern SUBREDDIT_ABOUT_RULES_API_REGEX = Pattern.compile("^https?://\\w+\\.reddit\\.com/r/(\\w+)/about/rules$");
    private static final Pattern SUBREDDIT_POSTS_API_REGEX = Pattern.compile("^https?://\\w+\\.reddit\\.com/r/(\\w+)/(?:hot|new|rising|top|controversial)");
    private final AutoSavingCache subredditCache = new AutoSavingCache("RedditSubreddits", 1000);
    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
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

    private Response handleAbout(Chain chain, Request request, String subredditName) throws IOException {
        Optional<String> cached = subredditCache.get(subredditName);
        if (cached.isPresent()) {
            return HttpUtils.makeJsonResponse(request, cached.get());
        }

        Response apiResponse = chain.proceed(request);
        if (apiResponse.isSuccessful()) {
            return apiResponse;
        }

        // Sometimes subreddits don't show up if searching by name, so attempt to get a post and retrieve the sub id from that.
        JsonNode samplePost = ArcticShift.getSubredditPosts(subredditName, 1).get(0);
        String subredditId = samplePost.get("subreddit_id").asText();

        JsonNode subreddit = ArcticShift.getSubredditInfoById(subredditId);
        subredditCache.put(subredditName, HttpUtils.getStringFromJson(subreddit));

        return HttpUtils.makeJsonResponse(request, subreddit);
    }

    private Response handleRules(Chain chain, Request request, String subredditName) throws IOException {
        Optional<String> cached = subredditCache.get(subredditName);
        if (!cached.isPresent()) {
            Response response = chain.proceed(request);
            if (response.isSuccessful()) {
                return response;
            }
        }

        EditableObjectNode rulesNode = new EditableObjectNode(
                Map.of(
                        "rules", new ArrayNode(JsonNodeFactory.instance),
                        "site_rules", new ArrayNode(JsonNodeFactory.instance),
                        "site_rules_flow", new ArrayNode(JsonNodeFactory.instance)
                )
        );
        return HttpUtils.makeJsonResponse(request, rulesNode);
    }

    private Response handlePosts(Chain chain, Request request, String subredditName) throws IOException {
        Optional<String> cached = subredditCache.get(subredditName);
        if (!cached.isPresent()) {
            Response response = chain.proceed(request);
            if (response.isSuccessful()) {
                return response;
            }
        }

        HttpUrl url = request.url();

        Integer limit = Optional.ofNullable(url.queryParameter("limit"))
                .map(Integer::parseInt)
                .orElse(null);
        String before = url.queryParameter("before");
        String after = url.queryParameter("after");
        String t = url.queryParameter("t");
        if (t != null && !"all".equals(t)) {
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
            after = afterDateTime.toLocalDate() + "T" + afterDateTime.toLocalTime();
        }

        List<JsonNode> children = new ArrayList<>();
        JsonNode rootNode = ArcticShift.getSubredditPosts(subredditName, limit, before, after);
        long earliestTimestamp = 365241780471L;
        long latestTimestamp = 0L;
        for (JsonNode node : rootNode) {
            EditableObjectNode child = EditableObjectNode.wrap(node);
            child.set(Emojis.EXTRA_EMOJI_CONTEXT_KEY, new TextNode(Emojis.PROHIBITED_EMOJI));
            child.set("saved", BooleanNode.FALSE);
            child.set("likes", NullNode.instance);

            children.add(new EditableObjectNode(Map.of(
                    "kind", new TextNode("t3"),
                    "data", child
            )));

            long timestamp = child.get("created_utc").asLong();
            if (timestamp < earliestTimestamp) {
                earliestTimestamp = timestamp;
            }
            if (timestamp > latestTimestamp) {
                latestTimestamp = timestamp;
            }
        }

        ObjectNode listing = RedditApiUtils.createListing(children);
        ObjectNode listingData = (ObjectNode) listing.get("data");
        listingData.replace("after", new TextNode(convertUnixTimestamp(earliestTimestamp)));
        listingData.replace("before", new TextNode(convertUnixTimestamp(latestTimestamp)));
        return HttpUtils.makeJsonResponse(request, listing);
    }

    private static String convertUnixTimestamp(long epochTime) {
        LocalDateTime ldt = LocalDateTime.ofEpochSecond(epochTime, 0, ZoneOffset.UTC);
        return ldt.toLocalDate() + "T" + ldt.toLocalTime();
    }
}
