/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 * Modified for Breal undelete audit: live-first, fail-open recovery (2026-10-12).
 */

package app.morphe.extension.boost.http.reddit;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.HttpURLConnection;

import app.morphe.extension.boost.utils.BoostUndeleteSettings;
import app.morphe.extension.shared.archive.HttpUtils;
import app.morphe.extension.shared.archive.wayback.WaybackMachine;
import app.morphe.extension.shared.archive.wayback.WaybackResponse;
import app.morphe.extension.shared.debug.LoggingUtils;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import okhttp3.Response;
import okhttp3.Request;


public class RedditMediaUndeleteInterceptor extends PatchedditInterceptor {

    private static final String FAIL_OPEN_MARKER = "morphe_boost_undelete_media_fail_open";

    @Override
    public boolean isEnabled() {
        return BoostUndeleteSettings.isRedditUndeleteEnabled();
    }

    @Override
    public boolean isPatchIncluded() {
        // To be overridden by patch.
        return false;
    }

    @NonNull
    @Override
    protected Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();

        String host = request.url().host();

        String contentUrl;
        if (host.contains("i.redd.it") || host.contains("preview.redd.it")) {
            contentUrl = request.url().toString();
        } else if (request.url().toString().contains("reddit.com/gallery/")) {
            // TODO: Handle galleries
            return chain.proceed(request);
        } else {
            return chain.proceed(request);
        }

        // Reddit stays authoritative: cached archive copies are only used after a live 404.
        Response response = chain.proceed(request);
        if (response.code() != HttpURLConnection.HTTP_NOT_FOUND) {
            return response;
        }
        response.close();

        try {
            if (!hasFileExtension(contentUrl)) {
                String modifiedUrl = contentUrl.split("\\?")[0] + ".jpeg";
                Request modifiedRequest = new Request.Builder()
                        .get()
                        .url(modifiedUrl)
                        .build();
                response = chain.proceed(modifiedRequest);
                if (response.code() != HttpURLConnection.HTTP_NOT_FOUND) {
                    return response;
                }
                response.close();
            }

            WaybackResponse waybackResponse = WaybackMachine.getFromWayback(request, contentUrl);
            if (waybackResponse.found()) {
                return waybackResponse.getResponse();
            } else if (!hasFileExtension(contentUrl)) {
                String modifiedUrl = contentUrl.split("\\?")[0] + ".jpeg";
                Request modifiedRequest = new Request.Builder()
                        .get()
                        .url(modifiedUrl)
                        .build();
                waybackResponse = WaybackMachine.getFromWayback(modifiedRequest, modifiedUrl);
            }
            return waybackResponse.getResponse();
        } catch (Throwable failure) {
            // The live answer was a 404; keep that rather than surfacing an archive failure.
            LoggingUtils.logInfo(false, () -> FAIL_OPEN_MARKER + ": " + failure.getClass().getSimpleName());
            return HttpUtils.create404(request);
        }
    }

    private boolean hasFileExtension(String url) {
        if (url == null || url.isBlank()) return false;
        if (url.contains("?")) {
            url = url.split("\\?")[0];
        }
        String lastSegment = url.substring(url.lastIndexOf('/') + 1);
        return lastSegment.lastIndexOf('.') > 0;
    }
}
