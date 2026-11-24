package app.morphe.extension.shared.fixes.slink;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.CookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Network transport for already validated links; never follows redirects itself or reads response bodies. */
public final class SafeRedirects {
    private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    private final ConnectionFactory factory;

    @FunctionalInterface
    public interface ConnectionFactory {
        HttpURLConnection open(URI uri) throws IOException;
    }

    public SafeRedirects() {
        this.factory = null;
    }

    public SafeRedirects(ConnectionFactory factory) {
        this.factory = factory;
    }

    /** Opens a validated URI without an ambient cookie store. Callers must disable redirects. */
    public static HttpURLConnection openConnection(URI uri) throws IOException {
        // Android snapshots the default CookieHandler when opening the connection. getDefault
        // and setDefault synchronize on this same class, so none can be installed in between.
        synchronized (CookieHandler.class) {
            if (CookieHandler.getDefault() != null) throw new AmbientCookies();
            return (HttpURLConnection) uri.toURL().openConnection();
        }
    }

    private static final class AmbientCookies extends IOException {}

    public URI redirect(URI uri, String authorization) throws IOException {
        String location = request(uri, "HEAD", authorization);
        return location(uri, location);
    }

    private String request(URI uri, String method, String authorization) throws IOException {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining < 2) return null;
        int timeout = (int) Math.min(2000, remaining / 2);
        Reply reply;
        try {
            reply = connectionRequest(uri, method, authorization, timeout);
        } catch (AmbientCookies ignored) {
            // Some stock clients install a global Java jar when their own browser is first used.
            // Keep it untouched and use an isolated client for these redirect lookups instead.
            reply = CookieFreeClient.request(uri, method, authorization, timeout);
        }
        if (System.nanoTime() >= deadline) return null;
        switch (reply.status) {
            case 301: case 302: case 303: case 307: case 308:
                return reply.location;
            default:
                boolean get = "HEAD".equals(method) && (reply.status >= 200 && reply.status < 300
                        || reply.status == 400 || reply.status == 401 || reply.status == 403
                        || reply.status == 405 || reply.status == 501);
                return get ? request(uri, "GET", authorization) : null;
        }
    }

    private Reply connectionRequest(URI uri, String method, String authorization, int timeout) throws IOException {
        HttpURLConnection connection = factory == null ? openConnection(uri) : factory.open(uri);
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setUseCaches(false);
            if (authorization != null) connection.setRequestProperty("Authorization", authorization);
            return new Reply(connection.getResponseCode(), connection.getHeaderField("Location"));
        } finally {
            connection.disconnect();
        }
    }

    private static final class Reply {
        final int status;
        final String location;

        Reply(int status, String location) {
            this.status = status;
            this.location = location;
        }
    }

    private static final class CookieFreeClient {
        private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .authenticator(Authenticator.NONE)
                .proxyAuthenticator(Authenticator.NONE)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();

        static Reply request(URI uri, String method, String authorization, int timeout) throws IOException {
            Request.Builder request = new Request.Builder().url(uri.toASCIIString()).method(method, null);
            if (authorization != null) request.header("Authorization", authorization);
            OkHttpClient client = CLIENT.newBuilder()
                    .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .readTimeout(timeout, TimeUnit.MILLISECONDS)
                    .callTimeout(2L * timeout, TimeUnit.MILLISECONDS)
                    .build();
            try (Response response = client.newCall(request.build()).execute()) {
                return new Reply(response.code(), response.header("Location"));
            }
        }
    }

    public static URI httpsUri(String value) {
        if (value == null || value.length() > 4096) return null;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getHost().equalsIgnoreCase(uri.getRawAuthority())
                    && uri.getRawPath() != null && !hasUnsafeSegment(uri.getRawPath()) ? uri : null;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    public static boolean hasHost(URI uri, String... hosts) {
        if (uri == null) return false;
        for (String host : hosts) if (host.equalsIgnoreCase(uri.getHost())) return true;
        return false;
    }

    public static URI location(URI base, String location) {
        if (location == null || location.isEmpty() || location.length() > 4096 || location.startsWith("//")) return null;
        try {
            URI raw = new URI(location);
            if (raw.getRawPath() == null || hasUnsafeSegment(raw.getRawPath())) return null;
            return httpsUri(base.resolve(raw).toString());
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static boolean hasUnsafeSegment(String path) {
        for (String segment : path.split("/", -1)) {
            String decoded = decode(segment);
            if (decoded == null || ".".equals(decoded) || "..".equals(decoded) || decoded.indexOf('\\') >= 0
                    || decoded.indexOf('\r') >= 0 || decoded.indexOf('\n') >= 0 || decoded.indexOf(0) >= 0) return true;
            // Encoded slashes may be legitimate payloads in a Reddit mail /CL0 segment. Consumers
            // validate path shape before opening a connection or returning a URL.
        }
        return false;
    }

    public static String query(URI uri, String name) {
        String query = uri == null ? null : uri.getRawQuery();
        if (query == null) return null;
        String value = null;
        for (String field : query.split("&")) {
            int equals = field.indexOf('=');
            if (equals < 0 || !name.equalsIgnoreCase(decode(field.substring(0, equals)))) continue;
            if (value != null) return null;
            value = decode(field.substring(equals + 1));
        }
        return value;
    }

    public static String decode(String encoded) {
        try {
            return URLDecoder.decode(encoded, "UTF-8");
        } catch (IllegalArgumentException ignored) {
            return null;
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
