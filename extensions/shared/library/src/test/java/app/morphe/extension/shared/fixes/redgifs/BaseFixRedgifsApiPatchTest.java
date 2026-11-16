/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.redgifs;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

public class BaseFixRedgifsApiPatchTest {
    private static OkHttpClient client(BaseFixRedgifsApiPatch.TokenProvider tokens, Interceptor server) {
        BaseFixRedgifsApiPatch patch = new BaseFixRedgifsApiPatch(tokens) {
            @Override
            public String getDefaultUserAgent() {
                return "fixture-agent";
            }
        };
        return new OkHttpClient.Builder().addInterceptor(patch::doIntercept).addInterceptor(server).build();
    }

    private static Response response(Request request, int code) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture").body(ResponseBody.create("{}", MediaType.get("application/json")))
                .build();
    }

    private static RedgifsTokenManager.RedgifsToken token() {
        return new RedgifsTokenManager.RedgifsToken("fresh-token", System.currentTimeMillis() / 1000);
    }

    @Test
    public void emulatesRemovedInfoWithoutFetchingAuthorization() throws Exception {
        OkHttpClient client = client((agent, force) -> {
            throw new AssertionError("Info lookup fetched a token");
        }, chain -> { throw new AssertionError("Info lookup reached the network"); });
        try (Response response = client.newCall(new Request.Builder()
                .url("https://api.redgifs.com/info").build()).execute()) {
            assertEquals(200, response.code());
            assertEquals("0.0.0.0", new JSONObject(response.body().string()).getString("remote-addr"));
        }
    }

    @Test
    public void emulatesOAuthForBothLegacyExpirationFieldNames() throws Exception {
        OkHttpClient client = client((agent, force) -> {
            assertEquals("fixture-agent", agent);
            assertFalse(force);
            return token();
        }, chain -> { throw new AssertionError("Legacy OAuth reached the network"); });
        try (Response response = client.newCall(new Request.Builder()
                .url("https://api.redgifs.com/v2/oauth/client")
                .header("Authorization", "Basic legacy-client").build()).execute()) {
            JSONObject json = new JSONObject(response.body().string());
            assertEquals("fresh-token", json.getString("access_token"));
            assertEquals("Bearer", json.getString("token_type"));
            assertEquals(json.getLong("expiry_time"), json.getLong("expires_in"));
            assertTrue(json.getLong("expires_in") > 0);
        }
    }

    @Test
    public void retriesRejectedTokensWithFreshAuthorizationAndPreservesTheAgent() throws Exception {
        for (int rejected : new int[]{401, 403}) {
            List<String> tokens = new ArrayList<>();
            AtomicInteger requests = new AtomicInteger();
            OkHttpClient client = client((agent, force) -> {
                tokens.add(agent + ":" + force);
                return token();
            }, chain -> {
                Request request = chain.request();
                if (requests.getAndIncrement() == 0) {
                    // Some native chains omit User-Agent on their response's request.
                    return response(request.newBuilder().removeHeader("User-Agent").build(), rejected);
                }
                assertEquals("Bearer fresh-token", request.header("Authorization"));
                assertEquals("native-agent", request.header("User-Agent"));
                return response(request, 200);
            });
            try (Response response = client.newCall(new Request.Builder()
                    .url("https://api.redgifs.com/v2/gifs/fixture")
                    .header("User-Agent", "native-agent").header("Authorization", "Bearer stale").build())
                    .execute()) {
                assertEquals(200, response.code());
            }
            assertEquals(2, requests.get());
            assertEquals(List.of("native-agent:true"), tokens);
        }
    }

    @Test
    public void doesNotRetryOtherFailuresOrTouchOtherHosts() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        OkHttpClient client = client((agent, force) -> {
            throw new AssertionError("Unrelated response fetched a token");
        }, chain -> {
            requests.incrementAndGet();
            return response(chain.request(), 404);
        });
        for (String url : List.of("https://api.redgifs.com/v2/gifs/missing",
                "https://example.com/info", "http://api.redgifs.com/info",
                "https://api.redgifs.com:444/info")) {
            try (Response response = client.newCall(new Request.Builder().url(url)
                    .header("Authorization", "Bearer original").build()).execute()) {
                assertEquals(404, response.code());
                assertEquals("Bearer original", response.request().header("Authorization"));
            }
        }
        assertEquals(4, requests.get());
    }

}
