/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.redditisfun.ImgurAlbumsPatch;
import app.morphe.extension.redditisfun.MediaMetadata;
import app.morphe.extension.shared.fixes.media.CommentVideoLinks;
import okhttp3.Cache;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;

final class MediaLoader {
    static final String TAG = "PatchedditMedia";
    private static final int MIB = 1024 * 1024;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), task -> new Thread(task, "PatchedditMedia"));
    private static final Semaphore DECODES = new Semaphore(2);
    private static final Map<String, List<Callback<?>>> PENDING = new HashMap<>();
    private static final Map<String, Host> HOSTS = new HashMap<>();
    private static final LruCache<String, Long> FAILURES = new LruCache<>(256);
    private static final LruCache<String, Album> ALBUMS = new LruCache<>(128);
    private static final LruCache<String, byte[]> ENCODED = new LruCache<String, byte[]>(24 * MIB) {
        @Override protected int sizeOf(String key, byte[] value) { return value.length; }
    };
    private static final Pattern IMAGE = Pattern.compile(".*\\.(?:jpe?g|png|webp|gif|bmp)$");
    private static final Pattern OG = Pattern.compile(
            "<meta\\b[^>]{0,4096}>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2", Pattern.DOTALL);
    private static long decodedBytes;
    private static OkHttpClient client;

    interface Callback<T> { void accept(T value); }
    private interface Work<T> { T run() throws IOException; }

    static final class Item {
        final String image;
        final String video;
        final boolean dash;
        final int width, height;

        Item(String image, String video, boolean dash, int width, int height) {
            this.image = image;
            this.video = video;
            this.dash = dash;
            this.width = width;
            this.height = height;
        }
    }

    static final class Album {
        final List<Item> items;
        final long created = android.os.SystemClock.elapsedRealtime();
        Album(List<Item> items) { this.items = Collections.unmodifiableList(items); }
        Album(Item item) { this(Collections.singletonList(item)); }
    }

    private static final class Host {
        final Semaphore slots = new Semaphore(2);
        int users;
    }

    static final class Decoded {
        final Drawable drawable;
        final int nativeWidth;
        private final long bytes;
        private boolean closed;

        Decoded(Drawable drawable, long bytes, int nativeWidth) {
            this.drawable = drawable; this.bytes = bytes; this.nativeWidth = nativeWidth;
        }

        void close() {
            if (closed) return;
            closed = true;
            if (drawable instanceof Animatable) ((Animatable) drawable).stop();
            drawable.setCallback(null);
            synchronized (MediaLoader.class) { decodedBytes -= bytes; }
        }
    }

    private MediaLoader() {}

    static synchronized OkHttpClient client(Context context) {
        if (client == null) {
            File[] stale = context.getCacheDir().listFiles((directory, name) -> name.startsWith("inline-frame-"));
            if (stale != null) for (File file : stale) {
                if (!file.delete()) Log.w(TAG, "Could not remove a stale inline video preview");
            }
            client = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
                    .followRedirects(false).followSslRedirects(false)
                    .cache(new Cache(new File(context.getCacheDir(), "patcheddit-media"), 100L * MIB))
                    .dns(host -> {
                        List<InetAddress> addresses = Dns.SYSTEM.lookup(host);
                        for (InetAddress address : addresses) {
                            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                                    || address.isMulticastAddress()
                                    || (address.getAddress().length == 16 && (address.getAddress()[0] & 0xfe) == 0xfc)) {
                                throw new UnknownHostException("Non-public media destination");
                            }
                        }
                        return addresses;
                    }).addNetworkInterceptor(chain -> {
                        Response response = chain.proceed(chain.request());
                        // Public media only: share a bounded disk cache, never an account's HTTP client.
                        if (response.isSuccessful() || response.code() == 404 || response.code() == 410) {
                            return response.newBuilder().header("Cache-Control", response.isSuccessful()
                                    ? "public, max-age=604800" : "public, max-age=86400").build();
                        }
                        return response;
                    }).build();
        }
        return client;
    }

    static HttpUrl publicUrl(String value) {
        if (value == null || value.length() > 4096) return null;
        HttpUrl url = HttpUrl.parse(value);
        return url != null && url.isHttps() && url.port() == 443
                && url.username().isEmpty() && url.password().isEmpty() ? url : null;
    }

    static String normalize(String value) {
        HttpUrl url = secureUrl(value);
        if (url == null) return null;
        if (host(url.host(), "reddit.com") && url.encodedPath().equals("/media")) {
            String wrapped = url.queryParameter("url");
            url = secureUrl(wrapped);
            if (url == null || !(redditMedia(url) || matrix(url))) return null;
        }
        return url.toString();
    }

    private static HttpUrl secureUrl(String value) {
        if (value == null || value.length() > 4096) return null;
        HttpUrl url = HttpUrl.parse(value);
        if (url != null && url.scheme().equals("http") && url.port() == 80) {
            url = url.newBuilder().scheme("https").port(443).build();
        }
        return url == null ? null : publicUrl(url.toString());
    }

    private static boolean redditMedia(HttpUrl url) {
        return url.host().equals("i.redd.it") || url.host().equals("preview.redd.it")
                || url.host().equals("external-preview.redd.it") || url.host().equals("v.redd.it");
    }

    private static boolean matrix(HttpUrl url) {
        return url.host().equals("matrix.redditspace.com") && RifMediaLinks.isMatrixPath(url.encodedPath());
    }

    static boolean supported(String value, boolean videos) {
        String normalized = normalize(value);
        if (normalized == null) return false;
        HttpUrl url = publicUrl(normalized);
        String path = url.encodedPath().toLowerCase(Locale.ROOT);
        String host = url.host();
        return IMAGE.matcher(path).matches() || host.equals("i.redd.it") || host.equals("preview.redd.it")
                || host.equals("external-preview.redd.it") || matrix(url)
                || host(host, "giphy.com") || host(host, "imgur.com") || host(host, "redgifs.com")
                || host(host, "gfycat.com") || host(host, "tenor.com") || host(host, "imgflip.com")
                || ((host.equals("reddit.com") || host.equals("www.reddit.com")) && path.startsWith("/gallery/"))
                || (videos && redditBase(normalized) != null);
    }

    static boolean host(String actual, String domain) {
        return actual.equals(domain) || actual.endsWith("." + domain);
    }

    private static String redditBase(String value) {
        HttpUrl url = publicUrl(value);
        if (url != null && url.host().equals("v.redd.it")
                && url.encodedPath().matches("/[A-Za-z0-9]{1,64}/?")) {
            return "https://v.redd.it/" + url.pathSegments().get(0);
        }
        return CommentVideoLinks.baseUrl(value);
    }

    static String videoKey(String value) {
        String base = redditBase(value);
        return base == null ? normalize(value) : base;
    }

    static void resolve(Context context, String url, Callback<Album> callback) {
        Album cached = ALBUMS.get(url);
        if (cached != null && android.os.SystemClock.elapsedRealtime() - cached.created < 7L * 24 * 60 * 60 * 1000) {
            callback.accept(cached);
            return;
        }
        submit("resolve:" + url, () -> {
            if (!InlineMedia.enabled()) return null;
            client(context);
            Album result = resolve(context, publicUrl(normalize(url)));
            // DASH manifests are temporary local files; recreate them from the cached public metadata.
            if (result != null && !result.items.get(0).dash) ALBUMS.put(url, result);
            return result;
        }, callback);
    }

    private static Album resolve(Context context, HttpUrl url) throws IOException {
        if (url == null) throw new IOException("Unsupported media URL");
        String value = url.toString();
        String path = url.encodedPath();
        String reddit = redditBase(value);
        if (reddit != null) return redditVideo(context, reddit);
        if (host(url.host(), "giphy.com")) {
            String last = url.pathSegments().get(url.pathSegments().size() - 1);
            String id = last.contains("-") ? last.substring(last.lastIndexOf('-') + 1) : last;
            if (path.startsWith("/media/") && url.pathSegments().size() > 1) {
                id = url.pathSegments().get(Math.max(1, url.pathSegments().size() - 2));
            }
            if (!id.matches("[A-Za-z0-9]{1,64}")) throw new IOException("Unsupported Giphy identifier");
            return new Album(new Item("https://media.giphy.com/media/" + id + "/giphy.webp", null, false, 0, 0));
        }
        if (host(url.host(), "imgur.com") && path.matches("/(?:a|gallery)/[A-Za-z0-9]{1,32}/?")) {
            String id = url.pathSegments().get(1);
            Album album = imgurAlbum(context, id, false);
            if (album == null && path.startsWith("/gallery/")) album = imgurAlbum(context, id, true);
            if (album != null) return album;
        }
        if (url.host().equals("i.imgur.com") && path.toLowerCase(Locale.ROOT).matches("/[a-z0-9]+\\.(?:mp4|gifv)")) {
            String base = value.substring(0, value.indexOf('.', value.indexOf(".com/") + 5));
            return new Album(new Item(base + "h.jpg", base + ".mp4", false, 0, 0));
        }
        if (IMAGE.matcher(path.toLowerCase(Locale.ROOT)).matches() || redditMedia(url) || matrix(url)) {
            return new Album(new Item(value, null, false, 0, 0));
        }
        String html = new String(fetch(context, url, 256 * 1024, null), java.nio.charset.StandardCharsets.UTF_8);
        Matcher tags = OG.matcher(html);
        while (tags.find()) {
            Map<String, String> attributes = new HashMap<>();
            Matcher attribute = ATTRIBUTE.matcher(tags.group());
            while (attribute.find()) attributes.put(attribute.group(1).toLowerCase(Locale.ROOT), attribute.group(3));
            if ("og:image".equalsIgnoreCase(attributes.get("property"))
                    || "og:image".equalsIgnoreCase(attributes.get("name"))) {
                String image = attributes.get("content");
                HttpUrl discovered = image == null ? null : url.resolve(image.replace("&amp;", "&"));
                if (discovered != null && publicUrl(discovered.toString()) != null) {
                    return new Album(new Item(discovered.toString(), null, false, 0, 0));
                }
            }
        }
        throw new IOException("No supported media preview");
    }

    private static Album imgurAlbum(Context context, String id, boolean gallery) throws IOException {
        try {
            HttpUrl api = HttpUrl.get("https://api.imgur.com/3/" + (gallery ? "gallery/" : "") + "album/" + id);
            String authorization = new ImgurAlbumsPatch().getClientAuthorization();
            if (!authorization.matches("Client-ID [A-Za-z0-9]+")) throw new IOException("Missing Imgur authorization");
            String json = new String(fetch(context, api, 2 * MIB, authorization), java.nio.charset.StandardCharsets.UTF_8);
            MediaMetadata.checkJsonDepth(json, 32, "Imgur album");
            if (!new JSONObject(json).optBoolean("success")) return null;
            JSONArray images = new JSONObject(json).getJSONObject("data").optJSONArray("images");
            if (images == null || images.length() == 0) return null;
            if (images.length() > 256) throw new IOException("Imgur album is too large");
            List<Item> items = new ArrayList<>();
            for (int i = 0; i < images.length(); i++) {
                JSONObject image = images.getJSONObject(i);
                String link = image.optString("link");
                String video = image.optString("mp4");
                if (link.endsWith(".mp4")) video = link;
                if (publicUrl(link) == null || !publicUrl(link).host().equals("i.imgur.com")) continue;
                boolean movie = publicUrl(video) != null && publicUrl(video).host().equals("i.imgur.com");
                String preview = movie ? "https://i.imgur.com/" + image.getString("id") + "h.jpg" : link;
                if (publicUrl(preview) != null) items.add(new Item(preview, movie ? video : null, false,
                        image.optInt("width"), image.optInt("height")));
            }
            return items.isEmpty() ? null : new Album(items);
        } catch (JSONException exception) {
            throw new IOException("Invalid Imgur album metadata", exception);
        } catch (HttpFailure failure) {
            if (failure.code == 404 || failure.code == 410) return null;
            throw failure;
        }
    }

    static void decode(Context context, Item item, Callback<Decoded> callback) {
        // Decodes belong to a view, unlike the deduplicated encoded payloads.
        bytes(context, item.image, bytes -> {
            if (bytes == null) { callback.accept(null); return; }
            execute(() -> {
                Decoded result = null;
                try {
                    DECODES.acquire();
                    try { result = decode(context, bytes, item.dash); }
                    finally { DECODES.release(); }
                } catch (IOException | InterruptedException | OutOfMemoryError exception) {
                    if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                    Log.w(TAG, "Inline image decoding failed: " + exception.getClass().getSimpleName());
                }
                Decoded delivered = result;
                MAIN.post(() -> callback.accept(delivered));
            }, () -> callback.accept(null));
        });
    }

    private static Decoded decode(Context context, byte[] bytes, boolean frame) throws IOException {
        Bitmap bitmap = null;
        if (frame) {
            File temporary = File.createTempFile("inline-frame-", ".mp4", context.getCacheDir());
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try (FileOutputStream stream = new FileOutputStream(temporary)) {
                stream.write(bytes);
                retriever.setDataSource(temporary.getPath());
                int width = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int height = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                if (width <= 0 || height <= 0 || width > 8192 || height > 8192) throw new IOException("Invalid video dimensions");
                if ((long) width * height > 1024 * 1024) {
                    if (Build.VERSION.SDK_INT < 27) throw new IOException("Video preview is too large");
                    double ratio = Math.sqrt(1024.0 * 1024 / ((double) width * height));
                    bitmap = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            Math.max(1, (int) (width * ratio)), Math.max(1, (int) (height * ratio)));
                } else bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (bitmap == null) throw new IOException("Video has no preview frame");
            } catch (IllegalArgumentException | IllegalStateException exception) {
                throw new IOException("Invalid video preview", exception);
            } finally {
                retriever.release();
                if (!temporary.delete()) Log.w(TAG, "Could not remove inline video preview temporary file");
            }
        }
        BitmapFactory.Options dimensions = new BitmapFactory.Options();
        dimensions.inJustDecodeBounds = true;
        if (bitmap == null) BitmapFactory.decodeByteArray(bytes, 0, bytes.length, dimensions);
        int width = bitmap == null ? dimensions.outWidth : bitmap.getWidth();
        int height = bitmap == null ? dimensions.outHeight : bitmap.getHeight();
        if (width <= 0 || height <= 0 || width > 65536 || height > 65536) throw new IOException("Invalid image dimensions");
        double scale = Math.min(1, Math.sqrt(1024.0 * 1024 / ((double) width * height)));
        int targetWidth = Math.max(1, (int) (width * scale)), targetHeight = Math.max(1, (int) (height * scale));
        long reservation = (long) targetWidth * targetHeight * 16;
        synchronized (MediaLoader.class) {
            if (decodedBytes + reservation > 48L * MIB) {
                if (bitmap != null) bitmap.recycle();
                throw new IOException("Inline image memory budget exhausted");
            }
            decodedBytes += reservation;
        }
        boolean success = false;
        try {
            Drawable drawable;
            if (bitmap != null) {
                drawable = new BitmapDrawable(context.getResources(), bitmap);
            } else if (Build.VERSION.SDK_INT >= 28) {
                drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), (decoder, info, source) -> {
                    decoder.setTargetSize(targetWidth, targetHeight);
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } else {
                BitmapFactory.Options options = new BitmapFactory.Options();
                while (width / (options.inSampleSize == 0 ? 1 : options.inSampleSize) > targetWidth * 2
                        || height / (options.inSampleSize == 0 ? 1 : options.inSampleSize) > targetHeight * 2) {
                    options.inSampleSize = options.inSampleSize == 0 ? 2 : options.inSampleSize * 2;
                }
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (bitmap == null) throw new IOException("Invalid image data");
                drawable = new BitmapDrawable(context.getResources(), bitmap);
            }
            success = true;
            return new Decoded(drawable, reservation, width);
        } finally {
            if (!success) synchronized (MediaLoader.class) { decodedBytes -= reservation; }
        }
    }

    private static void bytes(Context context, String url, Callback<byte[]> callback) {
        byte[] cached = ENCODED.get(url);
        if (cached != null) { callback.accept(cached); return; }
        submit("bytes:" + url, () -> {
            byte[] result = fetch(context, publicUrl(url), 32 * MIB, null);
            ENCODED.put(url, result);
            return result;
        }, callback);
    }

    private static byte[] fetch(Context context, HttpUrl initial, int limit, String authorization) throws IOException {
        if (initial == null) throw new IOException("Unsafe media URL");
        Host host;
        synchronized (HOSTS) {
            host = HOSTS.get(initial.host());
            if (host == null) { host = new Host(); HOSTS.put(initial.host(), host); }
            host.users++;
        }
        try {
            host.slots.acquire();
            try {
                HttpUrl url = initial;
                for (int redirect = 0; redirect <= 3; redirect++) {
                    if (!InlineMedia.enabled()) throw new IOException("Inline media was disabled");
                    Request.Builder request = new Request.Builder().url(url);
                    if (authorization != null) request.header("Authorization", authorization);
                    try (Response response = client(context).newCall(request.build()).execute()) {
                        if (response.isRedirect()) {
                            String location = response.header("Location");
                            HttpUrl next = location == null ? null : url.resolve(location);
                            if (authorization != null || next == null || publicUrl(next.toString()) == null
                                    || (matrix(initial) && !redditMedia(next))) {
                                throw new IOException("Unsafe media redirect");
                            }
                            url = next;
                            continue;
                        }
                        if (!response.isSuccessful() || response.body() == null) throw new HttpFailure(response.code());
                        if (response.body().contentLength() > limit) throw new IOException("Media payload exceeds size limit");
                        Buffer buffer = new Buffer();
                        while (response.body().source().read(buffer, Math.min(8192, limit + 1L - buffer.size())) != -1) {
                            if (buffer.size() > limit) throw new IOException("Media payload exceeds size limit");
                        }
                        return buffer.readByteArray();
                    }
                }
                throw new IOException("Too many media redirects");
            } finally { host.slots.release(); }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Media load interrupted", exception);
        } finally {
            synchronized (HOSTS) { if (--host.users == 0) HOSTS.remove(initial.host()); }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void submit(String key, Work<T> work, Callback<T> callback) {
        synchronized (PENDING) {
            Long failure = FAILURES.get(key);
            if (failure != null && android.os.SystemClock.elapsedRealtime() - failure < 120_000) {
                callback.accept(null);
                return;
            }
            List<Callback<?>> waiting = PENDING.get(key);
            if (waiting != null) {
                if (waiting.size() < 64) waiting.add(callback);
                else { Log.w(TAG, "Too many copies of one inline media request"); callback.accept(null); }
                return;
            }
            if (PENDING.size() >= 36) { Log.w(TAG, "Inline media queue is full"); callback.accept(null); return; }
            waiting = new ArrayList<>();
            waiting.add(callback);
            PENDING.put(key, waiting);
        }
        execute(() -> {
            T value = null;
            try { value = work.run(); }
            catch (IOException exception) {
                Log.w(TAG, "Inline media unavailable: " + (exception instanceof HttpFailure
                        ? "HTTP " + ((HttpFailure) exception).code : exception.getClass().getSimpleName())
                        + ". The original link is still available.");
            }
            T delivered = value;
            MAIN.post(() -> {
                List<Callback<?>> callbacks;
                synchronized (PENDING) {
                    callbacks = PENDING.remove(key);
                    if (delivered == null) FAILURES.put(key, android.os.SystemClock.elapsedRealtime());
                }
                for (Callback<?> next : callbacks) ((Callback<T>) next).accept(delivered);
            });
        }, () -> {
            List<Callback<?>> callbacks;
            synchronized (PENDING) { callbacks = PENDING.remove(key); }
            for (Callback<?> next : callbacks) ((Callback<T>) next).accept(null);
        });
    }

    private static void execute(Runnable work, Runnable rejected) {
        try { WORKERS.execute(work); }
        catch (java.util.concurrent.RejectedExecutionException exception) {
            Log.w(TAG, "Inline media queue is full");
            rejected.run();
        }
    }

    private static Album redditVideo(Context context, String base) throws IOException {
        return RedditDash.resolve(context, base, fetch(context, HttpUrl.get(base + "/DASHPlaylist.mpd"), 256 * 1024, null));
    }

    private static final class HttpFailure extends IOException {
        final int code;
        HttpFailure(int code) { super("Media HTTP " + code); this.code = code; }
    }
}
