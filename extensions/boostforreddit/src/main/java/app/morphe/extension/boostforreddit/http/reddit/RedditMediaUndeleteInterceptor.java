package app.morphe.extension.boostforreddit.http.reddit;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.regex.Pattern;

import app.morphe.extension.boostforreddit.http.HttpUtils;
import app.morphe.extension.boostforreddit.http.wayback.WaybackMachine;
import app.morphe.extension.boostforreddit.http.wayback.WaybackResponse;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.Request;


public class RedditMediaUndeleteInterceptor implements Interceptor {

    private static final Pattern REDDIT_URL_NO_EXT = Pattern.compile("https://(?:i|preview)\\.reddit\\.com/\\w+");

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
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

        try {
            Optional<WaybackResponse> cachedResponse = WaybackMachine.fetchUrlFromCache(request, contentUrl);
            if (cachedResponse.isPresent()) {
                return cachedResponse.get().getResponse();
            }

            Response response = chain.proceed(request);
            if (response.code() != HttpURLConnection.HTTP_NOT_FOUND) {
                return response;
            }
            response.close();

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
        } catch (SocketTimeoutException e) {
            return HttpUtils.create503(request);
        }
    }

    private boolean hasFileExtension(String url) {
        if (url == null || url.isBlank()) return false;
        if (url.contains("?")) {
            url = url.split("\\?")[0];
        }
        return REDDIT_URL_NO_EXT.matcher(url).matches();
    }
}
