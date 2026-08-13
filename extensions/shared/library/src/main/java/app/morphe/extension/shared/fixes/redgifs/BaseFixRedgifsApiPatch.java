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

        final String path = request.url().encodedPath();
        Logger.printInfo(() -> "Redgifs: intercepted " + request.method() + " " + path);

        String userAgent = getDefaultUserAgent();

        if (request.header("Authorization") != null) {
            Response response;
            try {
                response = chain.proceed(request.newBuilder().header("User-Agent", userAgent).build());
            } catch (IOException ex) {
                Logger.printException(() -> "Redgifs: request failed (existing Authorization) for " + path, ex);
                throw ex;
            }
            if (response.isSuccessful()) {
                Logger.printInfo(() -> "Redgifs: request succeeded (existing Authorization) for " + path
                        + ", code=" + response.code());
                return response;
            }
            Logger.printInfo(() -> "Redgifs: request failed (existing Authorization) for " + path
                    + ", code=" + response.code() + "; refreshing token");
            // The cached token (if any) is presumably the one that was just rejected, since it's
            // what the app used to build its own Authorization header. isValid() only checks
            // time-based expiry, so without invalidating it here, the refreshToken() call below
            // would just hand back the same already-rejected token instead of minting a new one.
            RedgifsTokenManager.invalidateToken(userAgent);
            // It's possible that the user agent is being overwritten later down in the interceptor
            // chain, so make sure we grab the new user agent from the request headers.
            userAgent = response.request().header("User-Agent");
            response.close();
        }
        final String finalUserAgent = userAgent;

        try {
            // Emulate response for the old client IP lookup endpoint, which Redgifs has removed.
            // It was only ever used to populate the legacy "user-addr" query parameter, which
            // current Redgifs endpoints accept but ignore, so the actual value doesn't matter.
            if (path.equals("/info")) {
                Logger.printInfo(() -> "Redgifs: emulating /info response locally");
                return buildLocalJsonResponse(request, RedgifsTokenManager.getEmulatedIpResponseBody());
            }

            RedgifsTokenManager.RedgifsToken token;
            try {
                token = RedgifsTokenManager.refreshToken(userAgent);
            } catch (IOException ex) {
                Logger.printException(() -> "Redgifs: failed to obtain temporary token for user agent \""
                        + finalUserAgent + "\"", ex);
                throw ex;
            }

            // Emulate response for old OAuth endpoint
            if (path.equals("/v2/oauth/client")) {
                Logger.printInfo(() -> "Redgifs: emulating /v2/oauth/client response locally");
                String responseBody = RedgifsTokenManager.getEmulatedOAuthResponseBody(token);
                return buildLocalJsonResponse(request, responseBody);
            }

            Request modifiedRequest = request.newBuilder()
                    .header("Authorization", "Bearer " + token.getAccessToken())
                    .header("User-Agent", userAgent)
                    .build();
            Response response;
            try {
                response = chain.proceed(modifiedRequest);
            } catch (IOException ex) {
                Logger.printException(() -> "Redgifs: request failed for " + path, ex);
                throw ex;
            }
            Logger.printInfo(() -> "Redgifs: request for " + path + " returned code=" + response.code());
            return response;
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not parse Redgifs response", ex);
            throw new IOException(ex);
        }
    }

    private static Response buildLocalJsonResponse(Request request, String jsonBody) {
        return new Response.Builder()
                .message("OK")
                .code(HttpURLConnection.HTTP_OK)
                .protocol(Protocol.HTTP_1_1)
                .request(request)
                .header("Content-Type", "application/json")
                .body(ResponseBody.create(jsonBody, MediaType.get("application/json")))
                .build();
    }
}
