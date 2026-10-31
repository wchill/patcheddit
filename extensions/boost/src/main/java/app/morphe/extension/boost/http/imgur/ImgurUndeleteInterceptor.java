/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 * Modified for Breal undelete audit: fail-open recovery, correct album cache URL (2026-10-12).
 */

package app.morphe.extension.boost.http.imgur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import app.morphe.extension.boost.http.AutoSavingCache;
import app.morphe.extension.boost.http.HttpUtils;
import app.morphe.extension.boost.http.wayback.WaybackMachine;
import app.morphe.extension.boost.http.wayback.WaybackResponse;
import app.morphe.extension.shared.requests.PatchInterceptor;
import app.morphe.extension.boost.utils.BoostUndeleteSettings;
import app.morphe.extension.boost.utils.LoggingUtils;
import okhttp3.Response;
import okhttp3.Request;

public class ImgurUndeleteInterceptor extends PatchInterceptor {
    private static final AutoSavingCache imgurCache = new AutoSavingCache("Imgur", 100);
    private static final String FAIL_OPEN_MARKER = "morphe_boost_undelete_imgur_fail_open";

    @Override
    public boolean isPatchIncluded() {
        // To be overridden by patch.
        return false;
    }

    @NotNull
    @Override
    public Response doIntercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (!BoostUndeleteSettings.isImgurUndeleteEnabled()) {
            return chain.proceed(request);
        }

        String host = request.url().host();

        if (!host.contains("imgur.com") && !host.contains("imgur-apiv3.p.rapidapi.com")) {
            return chain.proceed(request);
        }

        try {
            if (host.contains("api.")) {
                // handle all the albums
                return handleApiCall(chain);
            }

            return handleImageCall(chain);
        } catch (SocketTimeoutException e) {
            return HttpUtils.create503(request);
        }
    }

    private static void logRecoveryFailure(Throwable failure) {
        LoggingUtils.logInfo(false, () -> FAIL_OPEN_MARKER + ": " + failure.getClass().getSimpleName());
    }

    private Response handleApiCall(Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();
        String imgurId = getImgurId(url);

        Optional<String> cachedUrl = imgurCache.get(imgurId);
        if (cachedUrl.isPresent()) {
            try {
                return emulateAlbumApiResponse(
                        HttpUtils.get(cachedUrl.get(), WaybackMachine.CONTENT_TIMEOUT_MS), imgurId);
            } catch (Throwable failure) {
                // Fall back to the live request rather than failing on a bad cache entry.
                logRecoveryFailure(failure);
            }
        }

        Response response = chain.proceed(request);
        // Imgur returns an image with 34641 bytes and HTTP 200 when country wide blocking is in effect.

        if (response.code() != HttpURLConnection.HTTP_NOT_FOUND) {
            return response;
        }

        try {
            WaybackResponse waybackResponse = WaybackMachine.getFromWayback(request, getAlbumUrl(imgurId));
            if (waybackResponse.found()) {
                // Cache the Wayback page, not the Imgur API URL: the cached path parses a Wayback timestamp.
                String waybackUrl = waybackResponse.getResponse().request().url().toString();
                Response restored = emulateAlbumApiResponse(waybackResponse.getResponse(), imgurId);
                imgurCache.put(imgurId, waybackUrl);
                response.close();
                return restored;
            }
        } catch (Throwable failure) {
            logRecoveryFailure(failure);
        }
        return response;
    }

    private Response handleImageCall(Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();
        String imgurId = getImgurId(url);
        if (imgurId.length() > 7) {
            imgurId = imgurId.substring(0, 7);
        }
        String ext = getImgurFileExtension(url);
        url = "https://i.imgur.com/" + imgurId + "." + ext;

        Optional<String> cachedUrl = imgurCache.get(imgurId + "." + ext);
        if (cachedUrl.isPresent()) {
            try {
                Response cached = HttpUtils.get(cachedUrl.get(), WaybackMachine.CONTENT_TIMEOUT_MS);
                if (cached.isSuccessful()) {
                    return cached;
                }
                cached.close();
            } catch (Throwable failure) {
                logRecoveryFailure(failure);
            }
        }

        Response response = chain.proceed(request);
        if (response.code() != HttpURLConnection.HTTP_NOT_FOUND) {
            if (response.priorResponse() == null || !response.priorResponse().isRedirect()) {
                return response;
            }
        }

        try {
            WaybackResponse waybackResponse = WaybackMachine.getFromWayback(request, url);
            if (waybackResponse.found()) {
                Response archived = waybackResponse.getResponse();
                if (archived.isSuccessful()) {
                    imgurCache.put(imgurId + "." + ext, archived.request().url().toString());
                    response.close();
                    return archived;
                }
                archived.close();
            }
        } catch (Throwable failure) {
            logRecoveryFailure(failure);
        }
        // Imgur's own answer (404 or the removed.png redirect) is better than a synthetic error.
        return response;
    }

    private String getImgurId(String url) {
        String[] parts = url.split("/");
        return parts[parts.length - 1].split("\\.")[0];
    }

    private String getImgurFileExtension(String url) {
        String[] parts = url.split("\\.");
        return parts[parts.length - 1];
    }

    private boolean isAlbum(String url) {
        return url.contains("/a/") || url.contains("/g/") || url.contains("/album/") || url.contains("/gallery/");
    }

    private String getAlbumUrl(String imgurId) {
        return "https://imgur.com/a/" + imgurId;
    }

    private Response emulateAlbumApiResponse(Response waybackResponse, String imgurId) throws IOException {
        String url = waybackResponse.request().url().toString();
        String timestamp = WaybackMachine.getTimestamp(url);

        String html = waybackResponse.body().string();
        String jsonStr = extractAlbumJson(html);
        JsonNode json = HttpUtils.getJsonFromString(jsonStr);

        Map<String, JsonNode> responseJson = new HashMap<>();
        responseJson.put("status", new IntNode(200));
        responseJson.put("success", BooleanNode.TRUE);

        Map<String, JsonNode> dataJson = new HashMap<>();
        dataJson.put("id", new TextNode(imgurId));
        dataJson.put("deletehash", new TextNode(""));
        dataJson.put("title", new TextNode(""));
        dataJson.put("description", new TextNode(""));
        dataJson.put("nsfw", NullNode.instance);
        dataJson.put("images", modifyImageNodes(json.get("items"), timestamp));
        dataJson.put("images_count", json.get("count"));
        dataJson.put("is_album", BooleanNode.TRUE);
        dataJson.put("cover", new TextNode(""));
        dataJson.put("account_id", new IntNode(0));
        dataJson.put("views", new IntNode(0));
        dataJson.put("link", new TextNode(url));
        dataJson.put("layout", new TextNode("blog"));
        dataJson.put("privacy", new TextNode("private"));
        dataJson.put("account_url", NullNode.instance);
        dataJson.put("datetime", new IntNode(0));
        dataJson.put("error", NullNode.instance);

        responseJson.put("data", new ObjectNode(JsonNodeFactory.instance, new LinkedHashMap<>(dataJson)));
        ObjectNode output = new ObjectNode(JsonNodeFactory.instance, new LinkedHashMap<>(responseJson));

        return HttpUtils.makeJsonResponse(waybackResponse.request(), output);
    }

    private static ArrayNode modifyImageNodes(JsonNode array, String timestamp) {
        ArrayNode newArray = new ArrayNode(JsonNodeFactory.instance);
        for (JsonNode node : array) {
            String ext = node.get("ext").asText();
            ObjectNode newNode = JsonNodeFactory.instance.objectNode();
            newNode.setAll((ObjectNode) node);
            String imgurUrl = String.format("https://i.imgur.com/%s%s", node.get("hash").asText(), ext);
            String link = WaybackMachine.getWaybackContentUrl(imgurUrl, timestamp);
            newNode.set("datetime", new IntNode(0));
            newNode.set("link", new TextNode(link));
            newNode.set("id", node.get("hash"));
            newNode.set("type", new TextNode("image/" + ext.substring(1)));
            newNode.set("views", new IntNode(0));
            newNode.set("bandwidth", new IntNode(0));
            newNode.set("mp4", NullNode.instance);
            newNode.set("gifv", NullNode.instance);
            newNode.set("animated", BooleanNode.FALSE);
            newNode.set("error", NullNode.instance);

            if (".gifv".equals(ext) || ".mp4".equals(ext)) {
                newNode.set("type", new TextNode("video/mp4"));
                newNode.set("animated", BooleanNode.TRUE);
                newNode.set(ext.substring(1), new TextNode(link));
            } else if (".webm".equals(ext)) {
                newNode.set("type", new TextNode("video/webm"));
                newNode.set("animated", BooleanNode.TRUE);
            } else if (".gif".equals(ext)) {
                newNode.set("animated", BooleanNode.TRUE);
            }
            newArray.add(newNode);
        }
        return newArray;
    }

    private static String extractAlbumJson(String html) {
        String json = getStringBetween("images      : ", ",\n", html);
        if (json != null) {
            return json;
        }
        json = getStringBetween("window.postDataJSON = ", "</script>", html);
        if (json != null) {
            return json.trim();
        }
        throw new IllegalArgumentException("Could not extract JSON");
    }

    private static String getStringBetween(String before, String after, String body) {
        int startIndex = body.indexOf(before) + before.length();
        int endIndex = body.indexOf(after, startIndex);

        if (startIndex >= before.length() && endIndex > startIndex) {
            return body.substring(startIndex, endIndex);
        }
        return null;
    }
}
