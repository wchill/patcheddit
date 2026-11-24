/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.feed;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import app.morphe.extension.shared.requests.RedditRouteResponseShim;
import okhttp3.Authenticator;
import okhttp3.CookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class RandomSubredditInterceptor extends RedditRouteResponseShim {
    private static final class Transport {
        static final OkHttpClient CLIENT = new OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .authenticator(Authenticator.NONE)
                .proxyAuthenticator(Authenticator.NONE)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
    }

    public RandomSubredditInterceptor() {
        super(request -> RandomSubreddit.matches(request.method(), request.url().uri()));
    }

    @Override
    public boolean isPatchIncluded() {
        return false;
    }

    @Override
    @NonNull
    protected Response shimResponse(@NonNull Chain chain, @NonNull Request request) throws IOException {
        URI original = request.url().uri();
        URI selected = RandomSubreddit.rewrite(original, request.header("Authorization"),
                request.header("User-Agent"), RandomSubredditInterceptor::fetch);
        return chain.proceed(request.newBuilder().url(selected.toASCIIString()).build());
    }

    private static String fetch(URI uri, String authorization, String agent, int timeoutMs) throws IOException {
        Request.Builder request = new Request.Builder().url(uri.toASCIIString()).header("Accept", "application/json");
        if (authorization != null) request.header("Authorization", authorization);
        if (agent != null) request.header("User-Agent", agent);
        OkHttpClient client = Transport.CLIENT.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build();
        try (Response response = client.newCall(request.build()).execute()) {
            if (!response.isSuccessful()) throw new RandomSubreddit.HttpFailure(response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Random subreddit lookup returned no body");
            return RandomSubreddit.read(body.byteStream());
        }
    }
}
