/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.infinity.http;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.fixes.api.FailedGalleryMetadataPatch;
import app.morphe.extension.shared.fixes.api.RedditInfoResponsePatch;
import app.morphe.extension.shared.fixes.comments.MoreChildrenResponsePatch;
import app.morphe.extension.shared.requests.RedditRouteResponseShim;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;

/** Lets the shared Reddit shims use Infinity's native application chain. */
public final class NativeRedditLookups {
    private static final RedditInfoResponsePatch INFO = new RedditInfoResponsePatch();
    private static final MoreChildrenResponsePatch MORE = new MoreChildrenResponsePatch();
    private static final FailedGalleryMetadataPatch GALLERY = new FailedGalleryMetadataPatch();
    private static final String[] RESPONSE_HEADERS = {"Content-Type", "Content-Encoding", "WWW-Authenticate"};

    private NativeRedditLookups() {}

    public static Object intercept(Object chain, Object nativeRequest) throws IOException {
        HttpUrl url = HttpUrl.parse(requestUrl(nativeRequest));
        if (url == null || !url.isHttps() || url.port() != 443 || !url.username().isEmpty()
                || !url.password().isEmpty() || !isHost(url.host())) return proceed(chain, nativeRequest);
        String verb = requestMethod(nativeRequest);
        String path = RedditRouteResponseShim.normalizePath(url.encodedPath());
        boolean children = MORE.isPatchIncluded() && "/api/morechildren".equals(path)
                && ("GET".equals(verb) || "POST".equals(verb));
        boolean lookup = INFO.isPatchIncluded() && (("/api/info".equals(path)
                && ("GET".equals(verb) || "POST".equals(verb))) || ("GET".equals(verb)
                && (path.startsWith("/by_id/") || path.contains("/comments")
                || path.startsWith("/message/") || path.startsWith("/user/"))));
        if (!children && !lookup) return proceed(chain, nativeRequest);

        Request.Builder request = new Request.Builder().url(url);
        if ("POST".equals(verb)) {
            Object form = nativeForm(nativeRequest);
            if (form == null) return proceed(chain, nativeRequest);
            List<?> names = formNames(form);
            List<?> values = formValues(form);
            FormBody.Builder encoded = new FormBody.Builder();
            for (int i = 0; i < names.size(); i++) {
                encoded.addEncoded((String) names.get(i), (String) values.get(i));
            }
            request.post(encoded.build());
        }
        String referer = requestReferer(nativeRequest);
        if (referer != null) request.header("Referer", referer);

        try (NativeChain transport = new NativeChain(chain, nativeRequest, request.build())) {
            Response result;
            if (children) {
                Response original = transport.proceed(transport.request);
                if (!RedditInfoResponsePatch.isBlocked(original)) return transport.handoff(original);
                original.close();
                result = MORE.intercept(transport);
            } else {
                result = INFO.intercept(transport);
            }
            return transport.handoff(result, children ? null : GALLERY.repairNativeResponse(result, transport.request));
        }
    }

    private static boolean isHost(String host) {
        return "oauth.reddit.com".equals(host) || "www.reddit.com".equals(host) || "reddit.com".equals(host);
    }

    private static final class NativeChain implements Interceptor.Chain, Closeable {
        final Object chain, nativeRequest;
        final Request request;
        final ArrayList<NativeBody> responses = new ArrayList<>();

        NativeChain(Object chain, Object nativeRequest, Request request) {
            this.chain = chain;
            this.nativeRequest = nativeRequest;
            this.request = request;
        }

        @Override public Request request() { return request; }

        @Override public Response proceed(Request next) throws IOException {
            Object original;
            if (next == request) {
                original = NativeRedditLookups.proceed(chain, nativeRequest);
            } else {
                HttpUrl a = request.url(), b = next.url();
                if (!"GET".equals(next.method()) || !a.scheme().equals(b.scheme())
                        || !a.host().equals(b.host()) || a.port() != b.port()
                        || !b.username().isEmpty() || !b.password().isEmpty()) {
                    throw new IOException("Unexpected Reddit lookup origin or method");
                }
                original = NativeRedditLookups.proceed(chain, focusedGet(nativeRequest, b.toString()));
            }
            NativeBody body = new NativeBody(original);
            responses.add(body);
            Response.Builder response = new Response.Builder().request(next).body(body)
                    .protocol(Protocol.HTTP_1_1).code(status(original)).message("");
            for (String name : RESPONSE_HEADERS) {
                String value = responseHeader(original, name);
                if (value != null) response.header(name, value);
            }
            return response.build();
        }

        Object handoff(Response response) throws IOException {
            return handoff(response, null);
        }

        Object handoff(Response response, String repairedGallery) throws IOException {
            ResponseBody body = response.body();
            if (repairedGallery == null && body instanceof NativeBody) {
                NativeBody nativeBody = (NativeBody) body;
                responses.remove(nativeBody);
                return nativeBody.original;
            }
            try (Response rewritten = response) {
                if (body == null || responses.isEmpty()) throw new IOException("Reddit lookup returned no body");
                Object template = body instanceof NativeBody ? ((NativeBody) body).original
                        : responses.get(responses.size() - 1).original;
                byte[] content = repairedGallery == null ? body.bytes()
                        : repairedGallery.getBytes(StandardCharsets.UTF_8);
                return jsonResponse(template, nativeRequest, content);
            }
        }

        @Override public void close() {
            for (NativeBody body : responses) body.close();
        }

        @Override public Connection connection() { return null; }
        @Override public Call call() { throw new UnsupportedOperationException(); }
        @Override public int connectTimeoutMillis() { throw new UnsupportedOperationException(); }
        @Override public int readTimeoutMillis() { throw new UnsupportedOperationException(); }
        @Override public int writeTimeoutMillis() { throw new UnsupportedOperationException(); }
        @Override public Interceptor.Chain withConnectTimeout(int timeout, java.util.concurrent.TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
        @Override public Interceptor.Chain withReadTimeout(int timeout, java.util.concurrent.TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
        @Override public Interceptor.Chain withWriteTimeout(int timeout, java.util.concurrent.TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NativeBody extends ResponseBody {
        final Object original;
        final BufferedSource source;

        NativeBody(Object original) {
            this.original = original;
            source = Okio.buffer(new Source() {
                final Buffer snapshot = new Buffer();
                boolean started, tooLarge, closed;

                @Override public long read(Buffer sink, long count) throws IOException {
                    if (closed) throw new IOException("Closed native Reddit response");
                    if (!started) {
                        started = true;
                        byte[] bytes = bodyPrefix(original, RedditInfoResponsePatch.MAX_CONTEXT_BYTES + 1);
                        tooLarge = bytes.length > RedditInfoResponsePatch.MAX_CONTEXT_BYTES;
                        snapshot.write(bytes);
                    }
                    if (snapshot.size() == 0 && tooLarge) throw new IOException("Reddit lookup body is too large");
                    return snapshot.read(sink, count);
                }

                @Override public Timeout timeout() { return Timeout.NONE; }

                @Override public void close() throws IOException {
                    if (closed) return;
                    closed = true;
                    ((Closeable) original).close();
                }
            });
        }

        @Override public MediaType contentType() {
            String header = responseHeader(original, "Content-Type");
            return header == null ? null : MediaType.parse(header);
        }
        @Override public long contentLength() { return bodyLength(original); }
        @Override public BufferedSource source() { return source; }
    }

    private static String requestUrl(Object request) { throw unbound(); }
    private static String requestMethod(Object request) { throw unbound(); }
    private static String requestReferer(Object request) { throw unbound(); }
    private static Object nativeForm(Object request) { throw unbound(); }
    private static List<?> formNames(Object form) { throw unbound(); }
    private static List<?> formValues(Object form) { throw unbound(); }
    private static Object proceed(Object chain, Object request) throws IOException { throw unbound(); }
    private static Object focusedGet(Object request, String url) { throw unbound(); }
    private static int status(Object response) { throw unbound(); }
    private static long bodyLength(Object response) { throw unbound(); }
    private static String responseHeader(Object response, String name) { throw unbound(); }
    private static byte[] bodyPrefix(Object response, long limit) throws IOException { throw unbound(); }
    private static Object jsonResponse(Object template, Object request, byte[] body) { throw unbound(); }

    private static IllegalStateException unbound() {
        return new IllegalStateException("Infinity Reddit lookup bridge not bound");
    }
}
