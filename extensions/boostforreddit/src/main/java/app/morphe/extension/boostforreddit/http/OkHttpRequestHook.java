package app.morphe.extension.boostforreddit.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import app.morphe.extension.boostforreddit.http.arcticshift.ArcticShiftThrottlingInterceptor;
import app.morphe.extension.boostforreddit.http.imgur.ImgurUndeleteInterceptor;
import app.morphe.extension.boostforreddit.http.reddit.RedditFixAudioInDownloadsInterceptor;
import app.morphe.extension.boostforreddit.http.reddit.RedditMediaUndeleteInterceptor;
import app.morphe.extension.boostforreddit.http.reddit.RedditSubmissionUndeleteInterceptor;
import app.morphe.extension.boostforreddit.http.reddit.RedditSubredditUndeleteInterceptor;
import app.morphe.extension.boostforreddit.http.wayback.WaybackThrottlingInterceptor;
import app.morphe.extension.boostforreddit.utils.LoggingUtils;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


/**
 * @noinspection unused
 */
public class OkHttpRequestHook {
    public static final String BYPASS_HOOK_TAG = "BypassHook";
    private static final OkHttpRequestHook instance = new OkHttpRequestHook();
    private final Map<String, StackTraceElement[]> callStackOfPendingRequests = Collections.synchronizedMap(new HashMap<>());

    private OkHttpRequestHook() {}

    public static OkHttpRequestHook getInstance() {
        return instance;
    }

    public static Call interceptRequest(Call call) {
        return instance.handleRequest(call);
    }

    public static Response interceptResponse(Response response) {
        return instance.handleResponse(response);
    }

    public Call handleRequest(Call call) {
        Request request = call.request();
        Object existingTag = request.tag();
        callStackOfPendingRequests.put(request.method() + " " + request.url(), Thread.currentThread().getStackTrace());
        LoggingUtils.logInfo(true, () -> String.format(Locale.ROOT, "OkHttp3 request: %s", request.url()));

        return call;
    }

    public Response handleResponse(Response response) {
        Request request = response.request();
        StackTraceElement[] stackTrace = callStackOfPendingRequests.remove(request.method() + " " + request.url());

        LoggingUtils.logInfo(response.isSuccessful(), () -> {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.ROOT, "OkHttp3 response: %d %s %s", response.code(), response.message(), response.request().url()));
            sb.append("Stack trace:");
            assert stackTrace != null;
            for (StackTraceElement element : stackTrace) {
                sb.append("\n").append(element.toString());
            }
            return sb.toString();
        });

        return response;
    }

    public static OkHttpClient.Builder installInterceptor(OkHttpClient.Builder builder) {
        return builder
            .addInterceptor(new ImgurUndeleteInterceptor())
            .addInterceptor(new RedditMediaUndeleteInterceptor())
            .addInterceptor(new RedditSubredditUndeleteInterceptor())
            .addInterceptor(new RedditSubmissionUndeleteInterceptor())
            .addInterceptor(new RedditFixAudioInDownloadsInterceptor())
            .addNetworkInterceptor(new WaybackThrottlingInterceptor())
            .addNetworkInterceptor(new ArcticShiftThrottlingInterceptor());
    }
}
