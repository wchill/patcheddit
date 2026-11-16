/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * Copyright 2026 trulow.
 * Adapted from https://github.com/wchill/patcheddit/pull/124.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.redgifs;

import androidx.annotation.NonNull;

import org.json.JSONException;

import java.io.IOException;
import java.net.HttpURLConnection;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public abstract class BaseFixRedgifsApiPatch extends PatchedditInterceptor {
    protected static BaseFixRedgifsApiPatch INSTANCE;
    interface TokenProvider {
        RedgifsTokenManager.RedgifsToken get(String userAgent, boolean forceRefresh)
                throws IOException, JSONException;
    }

    private final TokenProvider tokens;

    protected BaseFixRedgifsApiPatch() {
        this(RedgifsTokenManager::refreshToken);
    }

    BaseFixRedgifsApiPatch(TokenProvider tokens) {
        this.tokens = java.util.Objects.requireNonNull(tokens);
    }

    public abstract String getDefaultUserAgent();

    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    public Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!request.url().isHttps() || !request.url().host().equals("api.redgifs.com")
                || request.url().port() != 443 || !request.url().username().isEmpty()
                || !request.url().password().isEmpty()) {
            return chain.proceed(request);
        }

        String path = request.url().encodedPath();
        // Legacy clients use this value for the obsolete user-addr query parameter.
        if (path.equals("/info") && request.method().equals("GET")) {
            return localJson(request, "{\"remote-addr\":\"0.0.0.0\"}");
        }

        String userAgent = request.header("User-Agent");
        if (userAgent == null || userAgent.isEmpty()) userAgent = getDefaultUserAgent();
        if (userAgent == null || userAgent.isEmpty()) throw new IOException("Redgifs user agent is missing");
        boolean forceRefresh = false;

        if (!path.equals("/v2/oauth/client") && request.header("Authorization") != null) {
            Response response = chain.proceed(request.newBuilder().header("User-Agent", userAgent).build());
            if (response.code() != 401 && response.code() != 403) {
                return response;
            }
            String sentUserAgent = response.request().header("User-Agent");
            if (sentUserAgent != null && !sentUserAgent.isEmpty()) userAgent = sentUserAgent;
            response.close();
            // Network changes can invalidate a token before its cached expiration.
            forceRefresh = true;
        }

        try {
            RedgifsTokenManager.RedgifsToken token = tokens.get(userAgent, forceRefresh);

            // Emulate response for old OAuth endpoint
            if (path.equals("/v2/oauth/client")) {
                return localJson(request, RedgifsTokenManager.getEmulatedOAuthResponseBody(token));
            }

            Request modifiedRequest = request.newBuilder()
                    .header("Authorization", "Bearer " + token.getAccessToken())
                    .header("User-Agent", userAgent)
                    .build();
            return chain.proceed(modifiedRequest);
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not parse Redgifs response", ex);
            throw new IOException(ex);
        }
    }

    private static Response localJson(Request request, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(HttpURLConnection.HTTP_OK).message("OK")
                .header("Content-Type", "application/json").header("Cache-Control", "no-store")
                .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
    }

}
