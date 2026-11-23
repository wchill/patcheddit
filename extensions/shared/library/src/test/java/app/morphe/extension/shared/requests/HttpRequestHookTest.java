/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.requests;

import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.fixes.feed.RAllPatch;
import okhttp3.Interceptor;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class HttpRequestHookTest {
    private static final MediaType TEXT = MediaType.get("text/plain");

    @Test
    public void installsOnlyOneHandlerAfterRepeatedInstallationOrCloning() {
        TestHook.initialize();
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        assertSame(builder, TestHook.installInterceptor(builder));
        TestHook.installInterceptor(builder);
        assertEquals(2, builder.interceptors().size());
        assertEquals(1, builder.networkInterceptors().size());

        OkHttpClient.Builder clone = builder.build().newBuilder();
        TestHook.installInterceptor(clone);
        assertEquals(2, clone.interceptors().size());
        assertEquals(1, clone.networkInterceptors().size());
    }

    @Test
    public void routesRAllWithoutRewritingLookalikeHostsOrExistingSuffixes() throws IOException {
        RAllPatch patch = new RAllPatch() {
            @Override
            public boolean isPatchIncluded() {
                return true;
            }
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(patch)
                .addInterceptor(chain -> response(chain.request(), "transport"))
                .build();

        Request original = new Request.Builder()
                .url("https://oauth.reddit.com/r/all-news/hot?after=t3_abc")
                .header("X-Test", "preserved")
                .build();
        try (Response response = client.newCall(original).execute()) {
            assertEquals("/r/all-news/hot/.json", response.request().url().encodedPath());
            assertEquals("t3_abc", response.request().url().queryParameter("after"));
            assertEquals("preserved", response.request().header("X-Test"));
        }
        for (String url : List.of("https://notreddit.com/r/all", "https://reddit.com.evil.example/r/all",
                "https://oauth.reddit.com/r/all/.json", "https://oauth.reddit.com/r/all.json",
                "https://oauth.reddit.com/r/allthings", "https://oauth.reddit.com/r/all/about",
                "https://oauth.reddit.com/r/all/comments/abc/title", "https://oauth.reddit.com/r/java")) {
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                assertSame(request, response.request());
            }
        }
    }

    @Test
    public void rewritesLegacyGetWithoutDroppingListingParameters() {
        URI listing = URI.create("https://oauth.reddit.com/r/all/top/?t=week&after=t3_abc");
        assertEquals("https://oauth.reddit.com/r/all/top/.json?t=week&after=t3_abc",
                RAllPatch.rewriteLegacyListing("GET", listing).toString());
        assertNull(RAllPatch.rewriteLegacyListing("POST", listing));
        assertNull(RAllPatch.rewriteLegacyListing("GET", URI.create("https://oauth.reddit.com/r/allthings")));
    }

    @Test
    public void routesOnlyMatchingRedditHostsMethodsAndPaths() throws IOException {
        AtomicInteger shimCalls = new AtomicInteger();
        AtomicInteger transportCalls = new AtomicInteger();
        RedditRouteResponseShim shim = new RedditRouteResponseShim("/api/example", "GET") {
            @Override
            public boolean isPatchIncluded() {
                return true;
            }

            @Override
            protected Response shimResponse(Chain chain, Request request) {
                shimCalls.incrementAndGet();
                return response(request, "shim");
            }
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(shim)
                .addInterceptor(chain -> {
                    transportCalls.incrementAndGet();
                    return response(chain.request(), "transport");
                })
                .build();

        for (String path : List.of("/api/example", "/api/example.json", "/api/example/.json/",
                "/r/example/api/example.json", "/r/example/api/example/.json/")) {
            try (Response response = client.newCall(new Request.Builder()
                    .url("https://oauth.reddit.com" + path + "?raw_json=1")
                    .build()).execute()) {
                assertEquals("shim", response.body().string());
            }
        }
        for (String url : List.of(
                "https://notreddit.com/api/example",
                "https://reddit.com.evil.example/api/example",
                "https://oauth.reddit.com/api/example-other",
                "https://oauth.reddit.com/comments/example.json"
        )) {
            try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
                assertEquals("transport", response.body().string());
            }
        }
        try (Response response = client.newCall(new Request.Builder()
                .url("https://reddit.com/api/example")
                .post(new FormBody.Builder().build())
                .build()).execute()) {
            assertEquals("transport", response.body().string());
        }
        assertEquals(5, shimCalls.get());
        assertEquals(5, transportCalls.get());
    }

    @Test
    public void leavesDisabledHandlersUnchanged() throws IOException {
        RedditRouteResponseShim disabled = new RedditRouteResponseShim("/api/example", "GET") {
            @Override
            public boolean isPatchIncluded() {
                return false;
            }

            @Override
            protected Response shimResponse(Chain chain, Request request) {
                throw new AssertionError("Disabled route handler ran");
            }
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(disabled)
                .addInterceptor(chain -> response(chain.request(), "transport"))
                .build();
        Request request = new Request.Builder()
                .url("https://reddit.com/api/example")
                .header("X-Test", "preserved")
                .build();
        try (Response response = client.newCall(request).execute()) {
            assertEquals("transport", response.body().string());
            assertEquals("preserved", response.request().header("X-Test"));
        }
    }

    private static Response response(Request request, String body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(body, TEXT))
                .build();
    }

    private static final class TestHook extends BaseOkHttpRequestHook {
        static void initialize() {
            INSTANCE = new TestHook();
        }

        @Override
        protected List<Interceptor> getInterceptors() {
            return List.of(new TestInterceptor());
        }

        @Override
        protected List<Interceptor> getNetworkInterceptors() {
            return List.of(new TestInterceptor());
        }
    }

    private static final class TestInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            return chain.proceed(chain.request());
        }
    }
}
