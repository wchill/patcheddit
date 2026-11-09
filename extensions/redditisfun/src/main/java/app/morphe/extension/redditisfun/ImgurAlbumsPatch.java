/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.redditisfun;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.AsyncTimeout;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

public class ImgurAlbumsPatch implements Interceptor {
    static final long MAX_METADATA_BYTES = 2L * 1024 * 1024;
    static final int MAX_COVER_IMAGES = 1024;
    static final int MAX_JSON_DEPTH = 32;
    static final int MAX_LOADS = 4;
    static final int MAX_COVER_DECODES = 2;
    private static final Semaphore LOADS = new Semaphore(MAX_LOADS);
    private static final Semaphore COVER_DECODES = new Semaphore(MAX_COVER_DECODES);
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9]{1,32}");
    private static final Pattern TITLE_ID = Pattern.compile(
            "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*-([A-Za-z0-9]{7})");
    private static final Pattern AUTHORIZATION = Pattern.compile("Client-ID [A-Za-z0-9]+");
    private final int timeoutMillis;

    public ImgurAlbumsPatch() {
        this(15_000);
    }

    ImgurAlbumsPatch(int timeoutMillis) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Invalid Imgur timeout");
        this.timeoutMillis = timeoutMillis;
    }

    public boolean usePublicApi() {
        return false; // Bound to the included patch's Morphe switch.
    }

    public boolean recoverThumbnails() {
        return false;
    }

    public boolean normalizeAlbumLinks() {
        return false;
    }

    public String getClientAuthorization() {
        throw new IllegalStateException("Native Imgur authorization was not bound");
    }

    public static Uri buildImgurUri(String albumId, boolean isGallery) {
        return Uri.parse(publicUrl(albumId, isGallery).toString());
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        boolean publicApi = usePublicApi();
        boolean thumbnails = recoverThumbnails();
        boolean titles = normalizeAlbumLinks();
        if (!publicApi && !thumbnails && !titles) return chain.proceed(original);
        AlbumRoute route = AlbumRoute.parse(original);
        if (route == null) return chain.proceed(original);
        boolean recoverCover = route.cover && thumbnails;
        boolean publicRequest = route.cover ? recoverCover : publicApi;
        if (!publicRequest && !titles) return chain.proceed(original);

        String id = original.url().pathSegments().get(route.idIndex);
        String normalized = titles ? normalizeId(id) : id;
        if (!normalized.matches("[A-Za-z0-9-]{1,256}")) {
            throw new IOException("Invalid Imgur album identifier");
        }
        HttpUrl url = publicRequest ? publicUrl(normalized, route.gallery)
                : original.url().newBuilder().setPathSegment(route.idIndex, normalized)
                .query(null).fragment(null).build();
        if (!publicRequest && url.equals(original.url())) return chain.proceed(original);
        Request request = original.newBuilder().url(url).build();
        if (publicRequest) {
            String authorization = route.cover ? original.header("Authorization") : null;
            if (authorization == null || !AUTHORIZATION.matcher(authorization).matches()) {
                authorization = getClientAuthorization();
            }
            if (authorization == null || !AUTHORIZATION.matcher(authorization).matches()) {
                throw new IOException("Native Imgur client authorization is unavailable");
            }
            // A proxy request must not carry proxy credentials or Reddit cookies to Imgur.
            Headers.Builder headers = new Headers.Builder();
            for (int i = 0; i < original.headers().size(); i++) {
                String name = original.headers().name(i);
                if (safeHeader(name)) headers.add(name, original.headers().value(i));
            }
            request = request.newBuilder().headers(headers.build())
                    .header("Authorization", authorization)
                    .header("Accept-Encoding", "identity").build();
        }

        MetadataLoad load = new MetadataLoad(chain.call(), LOADS, timeoutMillis);
        boolean handedOff = false;
        try {
            Chain bounded = chain
                    .withConnectTimeout(boundedTimeout(chain.connectTimeoutMillis()), TimeUnit.MILLISECONDS)
                    .withReadTimeout(boundedTimeout(chain.readTimeoutMillis()), TimeUnit.MILLISECONDS)
                    .withWriteTimeout(boundedTimeout(chain.writeTimeoutMillis()), TimeUnit.MILLISECONDS);
            Response response = bounded.proceed(request);
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Imgur album response has no body");
            if (publicRequest && (!response.request().url().isHttps()
                    || !response.request().url().host().equals("api.imgur.com")
                    || response.request().url().port() != 443
                    || !response.request().url().username().isEmpty()
                    || !response.request().url().password().isEmpty()
                    || !response.request().url().encodedPath().equals(url.encodedPath()))) {
                response.close();
                throw new IOException("Imgur album redirected to an unsupported destination");
            }
            if (body.contentLength() > MAX_METADATA_BYTES) {
                response.close();
                throw new IOException("Imgur album metadata exceeds the size limit");
            }
            response = response.newBuilder().body(load.bound(body)).build();
            if (recoverCover && response.isSuccessful()) return repairThumbnail(response);
            handedOff = true;
            return response;
        } catch (IOException exception) {
            throw load.failure(exception);
        } finally {
            if (!handedOff) load.finish();
        }
    }

    static HttpUrl publicUrl(String id, boolean gallery) {
        HttpUrl.Builder builder = new HttpUrl.Builder().scheme("https").host("api.imgur.com")
                .addPathSegment("3");
        if (gallery) builder.addPathSegment("gallery");
        return builder.addPathSegment("album").addPathSegment(id).build();
    }

    static String normalizeId(String value) throws IOException {
        if (ID.matcher(value).matches()) return value;
        if (value.length() <= 256) {
            java.util.regex.Matcher match = TITLE_ID.matcher(value);
            if (match.matches()) return match.group(1);
        }
        throw new IOException("Invalid or unsupported Imgur album identifier");
    }

    private static boolean safeHeader(String name) {
        return name.equalsIgnoreCase("Accept") || name.equalsIgnoreCase("Accept-Language")
                || name.equalsIgnoreCase("User-Agent") || name.equalsIgnoreCase("Cache-Control")
                || name.equalsIgnoreCase("If-None-Match") || name.equalsIgnoreCase("If-Modified-Since");
    }

    private static int boundedTimeout(int nativeTimeout) {
        return nativeTimeout == 0 ? 10_000 : Math.min(nativeTimeout, 10_000);
    }

    private static Response repairThumbnail(Response response) throws IOException {
        try (Response closeable = response) {
            if (!COVER_DECODES.tryAcquire()) {
                throw new IOException("Too many Imgur thumbnail lookups; try again");
            }
            try {
                ResponseBody body = closeable.body();
                MediaType type = body.contentType();
                if (type == null || !type.type().equals("application")
                        || !(type.subtype().equals("json") || type.subtype().endsWith("+json"))) {
                    throw new IOException("Imgur album response is not JSON");
                }
                byte[] bytes = body.bytes();
                String text = ResponseBody.create(bytes, type).string();
                String repaired = repairCover(text);
                Response.Builder result = closeable.newBuilder().body(repaired.equals(text)
                        ? ResponseBody.create(bytes, type) : ResponseBody.create(repaired, type));
                if (!repaired.equals(text)) {
                    result.removeHeader("Content-Length").removeHeader("Content-Encoding")
                            .removeHeader("Transfer-Encoding").removeHeader("ETag").removeHeader("Last-Modified");
                }
                return result.build();
            } finally {
                COVER_DECODES.release();
            }
        }
    }

    static String repairCover(String text) throws IOException {
        checkJsonDepth(text);
        try {
            JSONTokener tokener = new JSONTokener(text);
            Object value = tokener.nextValue();
            if (!(value instanceof JSONObject) || tokener.nextClean() != 0) {
                throw new IOException("Imgur album response is not a JSON object");
            }
            JSONObject root = (JSONObject) value;
            if (!Boolean.TRUE.equals(root.opt("success"))) {
                throw new IOException("Imgur did not return a successful album");
            }
            JSONObject album = root.getJSONObject("data");
            Object cover = album.opt("cover");
            if (cover instanceof String && ID.matcher((String) cover).matches()) return text;
            JSONArray images = album.optJSONArray("images");
            if (images == null) throw new IOException("Imgur album has no image list");
            if (images.length() > MAX_COVER_IMAGES) {
                throw new IOException("Imgur album exceeds the thumbnail recovery item limit");
            }
            for (int i = 0; i < images.length(); i++) {
                JSONObject image = images.optJSONObject(i);
                if (usableImage(image)) {
                    album.put("cover", image.getString("id"));
                    return root.toString();
                }
            }
            throw new IOException("Imgur album has no usable thumbnail");
        } catch (JSONException exception) {
            throw new IOException("Could not read Imgur album metadata", exception);
        }
    }

    private static boolean usableImage(JSONObject image) {
        if (image == null || !(image.opt("id") instanceof String)) return false;
        String id = image.optString("id");
        if (!ID.matcher(id).matches()) return false;
        String type = image.optString("type");
        if (!(type.equals("image/jpeg") || type.equals("image/png") || type.equals("image/gif")
                || type.equals("image/webp") || type.equals("image/bmp") || type.equals("video/mp4"))) return false;
        HttpUrl link = HttpUrl.parse(image.optString("link"));
        return link != null && link.isHttps() && link.host().equals("i.imgur.com")
                && link.port() == 443 && link.username().isEmpty() && link.password().isEmpty()
                && link.pathSegments().size() == 1
                && link.pathSegments().get(0).matches(Pattern.quote(id) + "\\.(?:jpe?g|png|gif|webp|bmp|mp4)");
    }

    private static void checkJsonDepth(String text) throws IOException {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') quoted = false;
            } else if (character == '"') {
                quoted = true;
            } else if (character == '{' || character == '[') {
                if (++depth > MAX_JSON_DEPTH) throw new IOException("Imgur metadata nesting exceeds the limit");
            } else if (character == '}' || character == ']') {
                depth--;
            }
        }
    }

    private static final class AlbumRoute {
        final int idIndex;
        final boolean gallery;
        final boolean cover;

        AlbumRoute(int idIndex, boolean gallery, boolean cover) {
            this.idIndex = idIndex;
            this.gallery = gallery;
            this.cover = cover;
        }

        static AlbumRoute parse(Request request) {
            HttpUrl url = request.url();
            if (!request.method().equals("GET") || !url.isHttps() || url.port() != 443
                    || !url.username().isEmpty() || !url.password().isEmpty()) return null;
            List<String> path = url.pathSegments();
            int index;
            boolean legacy = url.host().equals("api.redditisfun.com");
            if (legacy && path.size() >= 5 && path.subList(0, 3).equals(
                    java.util.Arrays.asList("api", "imgur", "3"))) index = 3;
            else if (url.host().equals("api.imgur.com") && path.size() >= 3 && path.get(0).equals("3")) index = 1;
            else return null;
            boolean gallery = path.get(index).equals("gallery");
            if (gallery) index++;
            if (index >= path.size() || !path.get(index).equals("album")) return null;
            int idIndex = index + 1;
            boolean cover = legacy && path.size() == idIndex + 2 && path.get(idIndex + 1).equals("cover");
            return path.size() == idIndex + 1 || cover ? new AlbumRoute(idIndex, gallery, cover) : null;
        }
    }

    static final class MetadataLoad {
        private final Call call;
        private final Semaphore slots;
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AsyncTimeout timeout;
        private volatile boolean expired;

        MetadataLoad(Call call, Semaphore slots, int timeoutMillis) throws IOException {
            if (!slots.tryAcquire()) throw new IOException("Too many Imgur album loads; try again");
            this.call = call;
            this.slots = slots;
            timeout = new AsyncTimeout() {
                @Override
                protected void timedOut() {
                    expired = true;
                    MetadataLoad.this.call.cancel();
                    finish();
                }
            };
            timeout.timeout(timeoutMillis, TimeUnit.MILLISECONDS);
            timeout.enter();
        }

        void finish() {
            if (finished.compareAndSet(false, true)) {
                timeout.exit();
                slots.release();
            }
        }

        IOException failure(IOException cause) {
            if (!expired) return cause;
            InterruptedIOException failure = new InterruptedIOException("Imgur album load timed out");
            failure.initCause(cause);
            return failure;
        }

        ResponseBody bound(ResponseBody body) {
            BufferedSource source = Okio.buffer(new ForwardingSource(body.source()) {
                private long bytesRead;

                @Override
                public long read(@NonNull Buffer sink, long byteCount) throws IOException {
                    try {
                        if (expired || call.isCanceled()) throw new IOException("Imgur album load was canceled");
                        long count = super.read(sink, Math.min(byteCount, MAX_METADATA_BYTES + 1 - bytesRead));
                        if (expired) throw new IOException("Imgur album load timed out");
                        if (count == -1) finish();
                        else if ((bytesRead += count) > MAX_METADATA_BYTES) {
                            call.cancel();
                            throw new IOException("Imgur album metadata exceeds the size limit");
                        }
                        return count;
                    } catch (IOException exception) {
                        call.cancel();
                        finish();
                        throw failure(exception);
                    }
                }

                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        finish();
                    }
                }
            });
            return new ResponseBody() {
                @Override public MediaType contentType() { return body.contentType(); }
                @Override public long contentLength() { return body.contentLength(); }
                @NonNull @Override public BufferedSource source() { return source; }
            };
        }
    }
}
