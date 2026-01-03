package app.morphe.extension.boostforreddit.http.imgur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import app.morphe.extension.boostforreddit.utils.EditableObjectNode;
import app.morphe.extension.boostforreddit.http.AutoSavingCache;
import app.morphe.extension.boostforreddit.http.HttpUtils;
import app.morphe.extension.boostforreddit.http.wayback.WaybackMachine;
import app.morphe.extension.boostforreddit.http.wayback.WaybackResponse;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.Request;

public class ImgurUndeleteInterceptor implements Interceptor {
    private static final AutoSavingCache imgurCache = new AutoSavingCache("Imgur", 10000);

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
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

    private Response handleApiCall(Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();
        String imgurId = getImgurId(url);

        Optional<String> cachedUrl = imgurCache.get(imgurId);
        if (cachedUrl.isPresent()) {
            Response cachedResponse = HttpUtils.get(cachedUrl.get());
            return emulateAlbumApiResponse(cachedResponse, imgurId);
        }

        Response response = chain.proceed(request);
        if (response.code() != HttpURLConnection.HTTP_NOT_FOUND) {
            return response;
        } else {
            response.close();
        }

        WaybackResponse waybackResponse = WaybackMachine.getFromWayback(request, getAlbumUrl(imgurId));
        if (waybackResponse.found()) {
            imgurCache.put(imgurId, response.request().url().toString());
            return emulateAlbumApiResponse(waybackResponse.getResponse(), imgurId);
        }
        return HttpUtils.create404(response.request());
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
            return HttpUtils.get(cachedUrl.get());
        }

        Response response = chain.proceed(request);
        if (response.code() != HttpURLConnection.HTTP_NOT_FOUND) {
            if (response.priorResponse() == null || !response.priorResponse().isRedirect()) {
                return response;
            }
        }
        response.close();

        Optional<WaybackResponse> cachedResponse = WaybackMachine.fetchUrlFromCache(request, url);
        if (cachedResponse.isPresent()) {
            return cachedResponse.get().getResponse();
        }

        WaybackResponse waybackResponse = WaybackMachine.getFromWayback(request, url);
        if (waybackResponse.found()) {
            imgurCache.put(imgurId + "." + ext, waybackResponse.getContentUrl().get());
            return waybackResponse.getResponse();
        }
        return HttpUtils.create404(request);
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

        responseJson.put("data", new EditableObjectNode(dataJson));
        EditableObjectNode output = new EditableObjectNode(responseJson);

        return HttpUtils.makeJsonResponse(waybackResponse.request(), output);
    }

    private static ArrayNode modifyImageNodes(JsonNode array, String timestamp) {
        ArrayNode newArray = new ArrayNode(JsonNodeFactory.instance);
        for (JsonNode node : array) {
            String ext = node.get("ext").asText();
            EditableObjectNode newNode = new EditableObjectNode(node);
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
