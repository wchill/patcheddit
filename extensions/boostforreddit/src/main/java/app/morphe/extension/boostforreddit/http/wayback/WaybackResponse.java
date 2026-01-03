package app.morphe.extension.boostforreddit.http.wayback;

import java.io.Closeable;
import java.util.Optional;

import app.morphe.extension.boostforreddit.http.HttpUtils;
import okhttp3.Request;
import okhttp3.Response;

public class WaybackResponse implements Closeable {
    private final String timestamp;
    private final Response response;
    private final Request request;

    public WaybackResponse(Response response, String timestamp) {
        this.response = response;
        this.request = response.request();
        this.timestamp = timestamp;
    }

    public WaybackResponse(Request request) {
        this.response = null;
        this.request = request;
        this.timestamp = null;
    }

    public boolean found() {
        return response != null;
    }

    public Response getResponse() {
        if (!found()) {
            return HttpUtils.create404(request);
        }
        return response;
    }

    public Optional<String> getContentUrl() {
        if (response.isSuccessful()) {
            return Optional.of(response.request().url().toString());
        }
        return Optional.empty();
    }

    public Optional<String> getTimestamp() {
        return Optional.ofNullable(timestamp);
    }

    @Override
    public void close() {
        try {
            response.close();
        } catch (Exception ignored) {}
    }
}
