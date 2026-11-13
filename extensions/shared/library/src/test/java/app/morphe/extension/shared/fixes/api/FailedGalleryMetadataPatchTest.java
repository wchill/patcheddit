/*
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FailedGalleryMetadataPatchTest {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType HTML = MediaType.get("text/html");
    private final List<String> probed = new ArrayList<>();

    // ---- fixtures ----

    /** Fake prober: only URLs in {@code existing} exist. */
    private OkHttpClient client(Set<String> existing, String body) {
        FailedGalleryMetadataPatch patch = new FailedGalleryMetadataPatch((chain, original, url) -> {
            probed.add(url);
            return existing.contains(url)
                    ? FailedGalleryMetadataPatch.Probe.FOUND : FailedGalleryMetadataPatch.Probe.MISSING;
        });
        return new OkHttpClient.Builder().addInterceptor(patch::doIntercept)
                .addInterceptor(chain -> json(chain.request(), body)).build();
    }

    /**
     * Production prober through a real OkHttp chain. {@code images} answers i.redd.it requests;
     * Reddit hosts get {@code body}.
     */
    private OkHttpClient liveClient(String body, Interceptor images) {
        return new OkHttpClient.Builder()
                .addInterceptor(new FailedGalleryMetadataPatch()::doIntercept)
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    if (request.url().host().equals("i.redd.it")) {
                        probed.add(request.method() + " " + request.url() + " " + request.header("User-Agent"));
                        return images.intercept(chain);
                    }
                    return json(request, body);
                }).build();
    }

    private static Response json(Request request, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(ResponseBody.create(body, JSON)).build();
    }

    private static Response status(Request request, int code) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
                .message("x").body(ResponseBody.create("", MediaType.get("image/png"))).build();
    }

    private static JSONObject failedMetadata(String... ids) throws Exception {
        JSONObject metadata = new JSONObject();
        for (String id : ids) metadata.put(id, new JSONObject().put("status", "failed").put("id", id));
        return metadata;
    }

    private static JSONObject postData(String thumbnail, JSONObject metadata, String... ids) throws Exception {
        JSONArray items = new JSONArray();
        for (String id : ids) items.put(new JSONObject().put("media_id", id).put("id", 1));
        return new JSONObject().put("name", "t3_post").put("id", "post").put("is_gallery", true)
                .put("thumbnail", thumbnail).put("url", "https://www.reddit.com/gallery/post")
                .put("gallery_data", new JSONObject().put("items", items))
                .put("media_metadata", metadata);
    }

    private static JSONObject listing(JSONObject data) throws Exception {
        return new JSONObject().put("kind", "Listing").put("data", new JSONObject()
                .put("children", new JSONArray().put(new JSONObject().put("kind", "t3").put("data", data))));
    }

    private static JSONObject galleryPost(String thumbnail, JSONObject metadata, String... ids) throws Exception {
        return listing(postData(thumbnail, metadata, ids));
    }

    private static Response fetchResponse(OkHttpClient client, String path) throws Exception {
        return client.newCall(new Request.Builder().url("https://oauth.reddit.com" + path)
                .header("User-Agent", "fixture-agent").build()).execute();
    }

    private static JSONObject fetch(OkHttpClient client, String path) throws Exception {
        try (Response response = fetchResponse(client, path)) {
            return new JSONObject(response.body().string());
        }
    }

    private static JSONObject meta(JSONObject listing) throws Exception {
        return listing.getJSONObject("data").getJSONArray("children").getJSONObject(0)
                .getJSONObject("data").getJSONObject("media_metadata");
    }

    private static String url(JSONObject metadata, String id) throws Exception {
        return metadata.getJSONObject(id).getJSONObject("s").getString("u");
    }

    // ---- extension selection ----

    @Test
    public void rebuildsFailedItemsWithTheExtensionThatExists() throws Exception {
        JSONObject metadata = failedMetadata("aaa", "bbb")
                .put("ccc", new JSONObject().put("status", "valid").put("e", "Image")
                        .put("s", new JSONObject().put("u", "https://preview.redd.it/ccc.jpg?width=10")));
        String body = galleryPost("https://b.thumbs.redditmedia.com/x.jpg", metadata, "aaa", "bbb", "ccc").toString();
        OkHttpClient client = client(Set.of("https://i.redd.it/aaa.png", "https://i.redd.it/bbb.png"), body);

        JSONObject result = meta(fetch(client, "/api/info.json?id=t3_post"));
        assertEquals("valid", result.getJSONObject("aaa").getString("status"));
        assertEquals("https://i.redd.it/aaa.png", url(result, "aaa"));
        assertEquals("https://i.redd.it/bbb.png", url(result, "bbb"));
        assertEquals("https://preview.redd.it/ccc.jpg?width=10", url(result, "ccc"));
        // jpg miss + png hit for the first, then png is tried first for the second.
        assertEquals(List.of("https://i.redd.it/aaa.jpg", "https://i.redd.it/aaa.png",
                "https://i.redd.it/bbb.png"), probed);
    }

    @Test
    public void usesThumbnailExtensionHintFirst() throws Exception {
        JSONObject metadata = new JSONObject().put("aaa", new JSONObject().put("status", "unprocessed"));
        String body = galleryPost("https://preview.redd.it/aaa.jpeg?width=140&s=x", metadata, "aaa").toString();
        OkHttpClient client = client(Set.of("https://i.redd.it/aaa.jpeg"), body);
        JSONObject result = meta(fetch(client, "/api/info?id=t3_post"));
        assertEquals("https://i.redd.it/aaa.jpeg", url(result, "aaa"));
        assertEquals(List.of("https://i.redd.it/aaa.jpeg"), probed);
    }

    @Test
    public void marksGifsAsAnimated() throws Exception {
        String body = galleryPost("https://preview.redd.it/aaa.gif?width=140", failedMetadata("aaa"), "aaa").toString();
        JSONObject entry = meta(fetch(client(Set.of("https://i.redd.it/aaa.gif"), body), "/api/info?id=t3_post"))
                .getJSONObject("aaa");
        assertEquals("AnimatedImage", entry.getString("e"));
        assertEquals("image/gif", entry.getString("m"));
        assertEquals("https://i.redd.it/aaa.gif", entry.getJSONObject("s").getString("gif"));
    }

    @Test
    public void repairsArrayRootCommentThreads() throws Exception {
        JSONArray thread = new JSONArray()
                .put(galleryPost("default", failedMetadata("aaa"), "aaa"))
                .put(new JSONObject().put("kind", "Listing").put("data",
                        new JSONObject().put("children", new JSONArray())));
        OkHttpClient client = client(Set.of("https://i.redd.it/aaa.jpg"), thread.toString());
        try (Response response = fetchResponse(client, "/r/pics/comments/post/title.json")) {
            JSONArray result = new JSONArray(response.body().string());
            assertEquals("https://i.redd.it/aaa.jpg", url(meta(result.getJSONObject(0)), "aaa"));
        }
    }

    // ---- leaving responses alone ----

    @Test
    public void leavesItemAloneWhenNothingExists() throws Exception {
        String body = galleryPost("default", failedMetadata("aaa"), "aaa").toString();
        JSONObject result = meta(fetch(client(Set.of(), body), "/api/info?id=t3_post"));
        assertEquals("failed", result.getJSONObject("aaa").getString("status"));
        assertFalse(result.getJSONObject("aaa").has("s"));
        assertEquals(5, probed.size());
    }

    @Test
    public void ignoresListingsAndHealthyGalleries() throws Exception {
        String body = galleryPost("default", failedMetadata("aaa"), "aaa").toString();
        fetch(client(Set.of("https://i.redd.it/aaa.jpg"), body), "/r/pics/hot.json");
        fetch(client(Set.of("https://i.redd.it/aaa.jpg"), body), "/best");
        assertTrue(probed.isEmpty());

        JSONObject ok = new JSONObject().put("aaa", new JSONObject().put("status", "valid")
                .put("s", new JSONObject().put("u", "https://i.redd.it/aaa.jpg")));
        fetch(client(Set.of(), galleryPost("default", ok, "aaa").toString()), "/api/info?id=t3_post");
        assertTrue(probed.isEmpty());
    }

    @Test
    public void passesMalformedJsonThroughUnchanged() throws Exception {
        String body = "{\"media_metadata\":{\"a\":{\"status\":\"failed\"}},\"gallery_data\":";
        try (Response response = fetchResponse(client(Set.of(), body), "/api/info?id=t3_post")) {
            assertEquals(body, response.body().string());
        }
    }

    @Test
    public void capsProbesPerResponse() throws Exception {
        String[] ids = new String[30];
        for (int i = 0; i < ids.length; i++) ids[i] = "id" + i;
        String body = galleryPost("default", failedMetadata(ids), ids).toString();
        fetch(client(Set.of(), body), "/api/info?id=t3_post");
        assertEquals(80, probed.size());
    }

    // ---- production prober through a real OkHttp chain ----

    @Test
    public void productionProberSendsHeadWithTheClientsUserAgent() throws Exception {
        String body = galleryPost("default", failedMetadata("aaa", "bbb"), "aaa", "bbb").toString();
        OkHttpClient client = liveClient(body, chain ->
                status(chain.request(), chain.request().url().encodedPath().endsWith(".png") ? 200 : 404));
        JSONObject result = meta(fetch(client, "/api/info?id=t3_post"));
        assertEquals("https://i.redd.it/aaa.png", url(result, "aaa"));
        assertEquals("https://i.redd.it/bbb.png", url(result, "bbb"));
        assertEquals(List.of(
                "HEAD https://i.redd.it/aaa.jpg fixture-agent",
                "HEAD https://i.redd.it/aaa.png fixture-agent",
                "HEAD https://i.redd.it/bbb.png fixture-agent"), probed);
    }

    @Test
    public void stopsProbingAfterATransportErrorAndKeepsEarlierRepairs() throws Exception {
        String body = galleryPost("default", failedMetadata("aaa", "bbb", "ccc"), "aaa", "bbb", "ccc").toString();
        OkHttpClient client = liveClient(body, chain -> {
            if (chain.request().url().encodedPath().equals("/aaa.jpg")) return status(chain.request(), 200);
            throw new SocketTimeoutException("stalled");
        });
        try (Response response = fetchResponse(client, "/api/info?id=t3_post")) {
            assertEquals(200, response.code());
            JSONObject result = meta(new JSONObject(response.body().string()));
            assertEquals("https://i.redd.it/aaa.jpg", url(result, "aaa"));
            assertEquals("failed", result.getJSONObject("bbb").getString("status"));
            assertEquals("failed", result.getJSONObject("ccc").getString("status"));
        }
        // aaa.jpg found, bbb.jpg timed out, nothing else attempted.
        assertEquals(2, probed.size());
    }

    @Test
    public void treatsServerErrorsAsTransportFailures() throws Exception {
        String body = galleryPost("default", failedMetadata("aaa", "bbb"), "aaa", "bbb").toString();
        OkHttpClient client = liveClient(body, chain -> status(chain.request(), 503));
        JSONObject result = meta(fetch(client, "/api/info?id=t3_post"));
        assertEquals("failed", result.getJSONObject("aaa").getString("status"));
        assertEquals(1, probed.size());
    }

    // ---- composition with RedditInfoResponsePatch's blocked /api/info fallback ----

    @Test
    public void repairsTheListingBuiltByTheInfoFallback() throws Exception {
        JSONObject post = new JSONObject().put("kind", "t3")
                .put("data", postData("default", failedMetadata("aaa"), "aaa"));
        JSONArray thread = new JSONArray()
                .put(new JSONObject().put("kind", "Listing").put("data",
                        new JSONObject().put("children", new JSONArray().put(post))))
                .put(new JSONObject().put("kind", "Listing").put("data",
                        new JSONObject().put("children", new JSONArray())));
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new FailedGalleryMetadataPatch((chain, original, url) -> {
                    probed.add(url);
                    return url.endsWith(".jpg")
                            ? FailedGalleryMetadataPatch.Probe.FOUND : FailedGalleryMetadataPatch.Probe.MISSING;
                })::doIntercept)
                .addInterceptor(new RedditInfoResponsePatch(new RedditCommentContext(100))::doIntercept)
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    if (request.url().encodedPath().contains("/api/info")) {
                        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                                .code(403).message("Blocked").header("Content-Type", "text/html")
                                .body(ResponseBody.create("blocked", HTML)).build();
                    }
                    return json(request, thread.toString());
                }).build();
        JSONObject result = meta(fetch(client, "/api/info.json?id=t3_post&raw_json=1"));
        assertEquals("https://i.redd.it/aaa.jpg", url(result, "aaa"));
        assertEquals(List.of("https://i.redd.it/aaa.jpg"), probed);
    }

    @Test
    public void extensionFromMime() {
        assertEquals("png", FailedGalleryMetadataPatch.extensionFromMime("image/png"));
        assertEquals("jpg", FailedGalleryMetadataPatch.extensionFromMime("image/jpg"));
        assertEquals(null, FailedGalleryMetadataPatch.extensionFromMime("video/mp4"));
    }
}
