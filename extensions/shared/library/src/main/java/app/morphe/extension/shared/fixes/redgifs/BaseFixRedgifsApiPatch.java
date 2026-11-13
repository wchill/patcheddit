package app.morphe.extension.shared.fixes.redgifs;

import androidx.annotation.NonNull;

import org.json.JSONException;

import java.io.IOException;
import java.net.HttpURLConnection;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public abstract class BaseFixRedgifsApiPatch extends PatchedditInterceptor {
    protected static BaseFixRedgifsApiPatch INSTANCE;
    public abstract String getDefaultUserAgent();

    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    public Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!request.url().host().equals("api.redgifs.com")) {
            return chain.proceed(request);
        }

        String path = request.url().encodedPath();

        // Emulate response for the removed client IP endpoint. It only fills the legacy "user-addr"
        // query parameter, which current Redgifs endpoints ignore.
        if (path.equals("/info")) {
            return localJsonResponse(request, RedgifsTokenManager.getEmulatedIpResponseBody());
        }

        String userAgent = getDefaultUserAgent();
        boolean forceTokenRefresh = false;

        if (request.header("Authorization") != null) {
            Response response = chain.proceed(request.newBuilder().header("User-Agent", userAgent).build());
            if (response.isSuccessful()) {
                return response;
            }
            // It's possible that the user agent is being overwritten later down in the interceptor
            // chain, so make sure we grab the new user agent from the request headers.
            String sentUserAgent = response.request().header("User-Agent");
            if (sentUserAgent != null && !sentUserAgent.isEmpty()) {
                userAgent = sentUserAgent;
            }
            response.close();
            // Redgifs tokens are tied to the IP address that requested them, so a cached token can be
            // rejected before it expires (for example after switching networks). Don't retry with it.
            forceTokenRefresh = true;
        }

        try {
            RedgifsTokenManager.RedgifsToken token =
                    RedgifsTokenManager.refreshToken(userAgent, forceTokenRefresh);

            // Emulate response for old OAuth endpoint
            if (path.equals("/v2/oauth/client")) {
                return localJsonResponse(request, RedgifsTokenManager.getEmulatedOAuthResponseBody(token));
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

    private static Response localJsonResponse(Request request, String responseBody) {
        return new Response.Builder()
                .message("OK")
                .code(HttpURLConnection.HTTP_OK)
                .protocol(Protocol.HTTP_1_1)
                .request(request)
                .header("Content-Type", "application/json")
                .body(ResponseBody.create(responseBody, MediaType.get("application/json")))
                .build();
    }
}
