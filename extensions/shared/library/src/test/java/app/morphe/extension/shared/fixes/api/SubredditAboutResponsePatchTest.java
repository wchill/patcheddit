/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.api;

import org.json.JSONObject;
import org.junit.Test;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

public class SubredditAboutResponsePatchTest {
    private Response fetch(String url, String json, int code) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new SubredditAboutResponsePatch()::doIntercept)
                .addInterceptor(chain -> new Response.Builder().request(chain.request())
                        .protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                        .header("ETag", "original")
                        .body(ResponseBody.create(json, MediaType.get("application/json"))).build())
                .build();
        return client.newCall(new Request.Builder().url(url).build()).execute();
    }

    @Test
    public void suppliesMissingCountersWithoutLosingTheSidebar() throws Exception {
        String body = "{\"kind\":\"t5\",\"data\":{\"subscribers\":42,\"description_html\":\"sidebar\"}}";
        try (Response response = fetch("https://oauth.reddit.com/r/test/about.json", body, 200)) {
            JSONObject data = new JSONObject(response.body().string()).getJSONObject("data");
            assertEquals(0, data.getInt("accounts_active"));
            assertEquals(42, data.getInt("subscribers"));
            assertEquals("sidebar", data.getString("description_html"));
            assertNull(response.header("ETag"));
        }
    }

    @Test
    public void replacesNullCounters() throws Exception {
        String body = "{\"kind\":\"t5\",\"data\":{\"subscribers\":null,\"accounts_active\":null}}";
        try (Response response = fetch("https://www.reddit.com/r/test/about/.json", body, 200)) {
            JSONObject data = new JSONObject(response.body().string()).getJSONObject("data");
            assertEquals(0, data.getInt("accounts_active"));
            assertEquals(0, data.getInt("subscribers"));
        }
    }

    @Test
    public void preservesCompleteResponsesAndUnrelatedRoutes() throws Exception {
        String complete = "{\"kind\":\"t5\", \"data\":{\"subscribers\":42,\"accounts_active\":7}}";
        try (Response response = fetch("https://oauth.reddit.com/r/test/about", complete, 200)) {
            assertEquals(complete, response.body().string());
            assertEquals("original", response.header("ETag"));
        }
        String missing = "{\"kind\":\"t5\",\"data\":{}}";
        for (String url : new String[]{"https://example.com/r/test/about",
                "https://oauth.reddit.com/r/test/hot", "https://oauth.reddit.com/api/info"}) {
            try (Response response = fetch(url, missing, 200)) {
                assertEquals(missing, response.body().string());
            }
        }
        try (Response response = fetch("https://oauth.reddit.com/r/test/about", missing, 403)) {
            assertEquals(403, response.code());
            assertEquals(missing, response.body().string());
        }
    }
}
