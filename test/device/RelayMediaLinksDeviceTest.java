/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class RelayMediaLinksDeviceTest {
    private static final String KEY = "morphe_patch_open_reddit_media_links";
    private static final String HTTP = "app.morphe.extension.shared.http.libraries.okhttp3.";
    private static ClassLoader loader;
    private static Activity host, preview;
    private static Object fetcher, router;
    private static SharedPreferences preferences;
    private static final Map<String, byte[]> images = new HashMap<>();
    private static final List<String> requests = java.util.Collections.synchronizedList(new ArrayList<>());
    static final CountDownLatch SLOW = new CountDownLatch(1);
    static final CountDownLatch STABLE_IMAGE = new CountDownLatch(1);
    private static final String run = Long.toString(System.nanoTime());
    private static final String query = "?s=fixture%2Fsignature%3D&run=" + run;

    public static void run(Context context, Bundle arguments) throws Exception {
        loader = context.getClassLoader();
        preferences = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences backup = context.getSharedPreferences("patcheddit_relay_media_fixture", 0);
        if (backup.contains("pending")) restore(backup);
        if (preferences.contains(KEY)) backup.edit().putBoolean(KEY, preferences.getBoolean(KEY, true)).commit();
        else backup.edit().remove(KEY).commit();
        check(backup.edit().putBoolean("pending", true).commit(), "Could not save fixture recovery state");
        check(preferences.edit().remove(KEY).commit(), "Could not check the default media setting");
        try {
            Class<?> fetchType = type("reddit.news.previews.MediaUrlFetcher");
            Object[] dependencies = new Object[9];
            dependencies[8] = preferences;
            fetcher = fetchType.getConstructors()[0].newInstance(dependencies);
            router = type("reddit.news.utils.UrlLinkClickManager")
                    .getConstructor(SharedPreferences.class, fetchType).newInstance(preferences, fetcher);
            String target = arguments.getString("stock", "");
            boolean galleryOnly = target.equals("gallery") || target.equals("gallery-pro");
            transport(context, galleryOnly);
            var instrumentation = PatchedditInstrumentation.instance;
            String main = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName())
                    .getComponent().getClassName();
            var monitor = instrumentation.addMonitor(main, null, false);
            try {
                context.startActivity(context.getPackageManager().getLaunchIntentForPackage(context.getPackageName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                host = monitor.waitForActivityWithTimeout(15_000);
                check(host != null, "Relay's native test host did not start");
            } finally { instrumentation.removeMonitor(monitor); }
            if (galleryOnly) {
                verifyGallery(target.equals("gallery-pro") || context.getPackageName().equals("reddit.news"));
                return;
            }
            String[] hosts = {"i.redd.it", "preview.redd.it", "external-preview.redd.it",
                    "a.thumbs.redditmedia.com", "b.thumbs.redditmedia.com", "styles.redditmedia.com",
                    "emoji.redditmedia.com", "www.redditstatic.com"};
            for (String domain : hosts) for (String format : new String[]{"jpeg", "png", "gif", "webp"}) {
                String url = "https://" + domain + "/PatchedditFixture/test." + format + query;
                check((Boolean) fetchType.getMethod("k", String.class).invoke(fetcher, url), "CDN link rejected: " + url);
                Object descriptor = resolved(url);
                check(url.equals(field(descriptor, "mediaUrl")), "Signed media URL changed");
                check((Integer) field(descriptor, "type") == (format.equals("gif") || format.equals("webp") ? 3 : 1),
                        "Wrong native viewer type");
            }
            for (String domain : new String[]{"v.redd.it", "vcf.redd.it", "packaged-media.redd.it"}) {
                String url = "https://" + domain + "/PatchedditFixture/video.mp4" + query;
                check(url.equals(field(resolved(url), "mediaUrl")), "Signed video URL changed");
            }
            for (String url : new String[]{"https://v.redd.it/PatchedditFixture" + query,
                    "https://vcf.redd.it/link/post/asset/PatchedditFixture" + query,
                    "https://packaged-media.redd.it/PatchedditFixture/pb/DASHPlaylist.mpd" + query}) {
                Object descriptor = resolved(url);
                check(((String) field(descriptor, "mediaUrl")).endsWith("/video" + run + ".mp4"),
                        "Relative DASH video URL did not retain its CDN base");
                check(((String) field(descriptor, "mediaUrl")).startsWith(url.substring(0, url.indexOf('/', 8) + 1)),
                        "DASH video switched CDN hosts");
                check(((String) field(descriptor, "audioUrl")).endsWith("/audio" + run + ".m4a"),
                        "DASH audio was not retained");
            }
            String commentVideo = "https://www.reddit.com/link/post123/video/PatchedditFixture/player";
            check((Boolean) fetchType.getMethod("k", String.class).invoke(fetcher, commentVideo),
                    "Native router rejected the comment video");
            Object commentPreview = resolved(commentVideo);
            String commentBase = "https://v.redd.it/link/post123/asset/PatchedditFixture/";
            check((commentBase + "video" + run + ".mp4").equals(field(commentPreview, "mediaUrl")),
                    "Comment video lost its asset directory");
            check((commentBase + "audio" + run + ".m4a").equals(field(commentPreview, "audioUrl")),
                    "Comment video lost its audio track");
            try {
                resolved("https://v.redd.it/PatchedditFixture/broken.mpd" + query);
                throw new AssertionError("Malformed manifest returned success");
            } catch (java.io.IOException expected) { }
            Object redirected = resolved("https://v.redd.it/PatchedditFixture/redirect.mpd" + query);
            check(("https://vcf.redd.it/PatchedditFixture/moved/video" + run + ".mp4")
                    .equals(field(redirected, "mediaUrl")), "Redirected DASH used the original manifest base");
            for (String path : new String[]{"oversized", "outside"}) {
                try {
                    resolved("https://v.redd.it/PatchedditFixture/" + path + ".mpd" + query);
                    throw new AssertionError("Unsafe manifest returned success");
                } catch (java.io.IOException expected) {
                    check(expected.getMessage().contains(path.equals("oversized") ? "oversized" : "redirect"),
                            "Wrong manifest failure: " + expected);
                }
            }
            String still = "https://preview.redd.it/PatchedditFixture/test.jpeg" + query;
            show(still, false);
            await(() -> onMain(() -> (Boolean) invoke(currentFragment(), "scaleImageView", "isReady")),
                    "Native JPEG viewer did not decode the image");
            closePreview();
            for (String suffix : new String[]{"test.gif", "test.webp", "test.gif?format=webp&"}) {
                String url = "https://external-preview.redd.it/PatchedditFixture/" + suffix
                        + (suffix.contains("?") ? query.substring(1) : query);
                show(url, true);
                await(() -> onMain(() -> {
                    Object fragment = currentFragment();
                    if (fragment == null || !fragment.getClass().getName().endsWith("FragmentGifPreview")) return false;
                    ImageView image = (ImageView) field(fragment, "imageView");
                    return image != null && image.getDrawable() instanceof Animatable
                            && ((Animatable) image.getDrawable()).isRunning();
                }), "Native GIF/WebP viewer did not animate");
                closePreview();
            }
            for (String url : new String[]{"https://v.redd.it/PatchedditFixture/video.mp4" + query,
                    "https://v.redd.it/PatchedditFixture/HLSPlaylist.m3u8" + query,
                    "https://v.redd.it/PatchedditFixture/DASHPlaylist.mpd" + query,
                    commentVideo,
                    "https://vcf.redd.it/PatchedditFixture/DASHPlaylist.mpd" + query,
                    "https://packaged-media.redd.it/PatchedditFixture/DASHPlaylist.mpd" + query,
                    "https://external-preview.redd.it/PatchedditFixture/test.gif?format=mp4&" + query.substring(1)}) {
                show(url, false);
                await(() -> onMain(() -> {
                    Object fragment = currentFragment();
                    if (fragment == null || !fragment.getClass().getName().endsWith("FragmentVideoPreview")) return false;
                    Object player = typedField(fragment, type("com.google.android.exoplayer2.ExoPlayer")).get(fragment);
                    return player != null && (Long) type("com.google.android.exoplayer2.Player")
                            .getMethod("getCurrentPosition").invoke(player) > 0;
                }), "Native video/HLS/DASH playback did not advance");
                closePreview();
            }
            show(commentVideo, true);
            await(() -> onMain(() -> {
                Object fragment = currentFragment();
                return fragment != null && fragment.getClass().getName().endsWith("FragmentVideoPreview");
            }), "The comment link handler did not open the native video viewer");
            closePreview();
            check(requests.stream().anyMatch(still::equals), "Native loader dropped signing parameters");
            for (String domain : new String[]{"v.redd.it", "vcf.redd.it", "packaged-media.redd.it"}) {
                check(requests.stream().anyMatch(url -> url.startsWith("https://" + domain + "/")
                        && url.endsWith("/audio" + run + ".m4a")), "Native DASH playback omitted audio: " + domain);
            }
            check(requests.contains("https://v.redd.it/PatchedditFixture/HLSPlaylist.m3u8" + query),
                    "Native HLS loader dropped signing parameters");
            String live = arguments.getString("live-url");
            if (live != null) {
                show(live, true);
                await(() -> onMain(() -> (Boolean) invoke(currentFragment(), "scaleImageView", "isReady")),
                        "Live Reddit CDN image did not decode");
                closePreview();
            }
            if ("true".equals(arguments.getString("inline"))) {
                type("app.patcheddit.fixture.RelayInlineMediaDeviceTest")
                        .getMethod("verify", Context.class, Activity.class).invoke(null, context, host);
            }
            check(preferences.edit().putBoolean(KEY, false).commit(), "Could not disable the patch");
            check(!(Boolean) fetchType.getMethod("k", String.class).invoke(fetcher, still),
                    "Disabled patch still intercepted JPEG previews");
            check(type("app.morphe.extension.relay.media.MediaLinks")
                    .getMethod("commentVideoBase", String.class).invoke(null, commentVideo) == null,
                    "Disabled patch still normalized comment player URLs");
            check(type("app.morphe.extension.relay.media.MediaViewer")
                    .getMethod("resolve", Object.class, String.class).invoke(null, fetcher, still) == null,
                    "Disabled resolver did not fall through");
            check((Boolean) fetchType.getMethod("k", String.class)
                    .invoke(fetcher, "https://example.com/image.png"), "Unrelated native image routing changed");
        } finally {
            if (preview != null) closePreview();
            restore(backup);
            check(backup.edit().clear().commit(), "Could not clear fixture recovery state");
            if (host != null) onMain(() -> { host.finish(); return null; });
        }
    }

    private static void restore(SharedPreferences backup) {
        SharedPreferences.Editor edit = preferences.edit().remove(KEY);
        if (backup.contains(KEY)) edit.putBoolean(KEY, backup.getBoolean(KEY, true));
        check(edit.commit(), "Could not restore Relay's media preference");
    }

    private static Object resolved(String url) throws Exception {
        Object observable = fetcher.getClass().getMethod("u", String.class).invoke(fetcher, url);
        CountDownLatch done = new CountDownLatch(1);
        Object[] result = new Object[2];
        Class<?> action = type("rx.functions.Action1");
        Object success = Proxy.newProxyInstance(loader, new Class<?>[]{action}, (proxy, method, args) -> {
            result[0] = args[0]; done.countDown(); return null;
        });
        Object failure = Proxy.newProxyInstance(loader, new Class<?>[]{action}, (proxy, method, args) -> {
            result[1] = args[0]; done.countDown(); return null;
        });
        type("rx.Observable").getMethod("T", action, action).invoke(observable, success, failure);
        check(done.await(25, TimeUnit.SECONDS), "Media resolver timed out");
        if (result[1] instanceof Exception) throw (Exception) result[1];
        check(result[1] == null, "Media resolution failed: " + result[1]);
        return ((List<?>) result[0]).get(0);
    }

    private static void show(String url, boolean comments) throws Exception {
        var instrumentation = PatchedditInstrumentation.instance;
        var monitor = instrumentation.addMonitor("reddit.news.previews.ActivityPreview", null, false);
        try {
            onMain(() -> {
                if (comments) {
                    Object fragment = type("reddit.news.WebAndCommentsFragment").getConstructor().newInstance();
                    dependency(fragment, router);
                    dependency(fragment, fetcher);
                    Object manager = host.getClass().getMethod("getSupportFragmentManager").invoke(host);
                    set(fragment, "mHost", field(manager, "mHost"));
                    List<Method> methods = new ArrayList<>();
                    for (Method method : fragment.getClass().getDeclaredMethods()) {
                        if (Arrays.equals(method.getParameterTypes(), new Class<?>[]{String.class, boolean.class})
                                && method.getReturnType() == void.class) methods.add(method);
                    }
                    check(methods.size() == 1, "Ambiguous native comment link handler");
                    methods.get(0).invoke(fragment, url, true);
                } else {
                    router.getClass().getMethod("c", String.class, type("androidx.fragment.app.FragmentActivity"))
                            .invoke(router, url, host);
                }
                return null;
            });
            preview = monitor.waitForActivityWithTimeout(15_000);
            check(preview != null, "Link opened the browser instead of ActivityPreview: " + url);
            await(() -> onMain(() -> currentFragment() != null), "Native media fragment did not start");
        } finally { instrumentation.removeMonitor(monitor); }
    }

    private static Object currentFragment() throws Exception {
        Object manager = preview.getClass().getMethod("getSupportFragmentManager").invoke(preview);
        List<?> fragments = (List<?>) manager.getClass().getMethod("getFragments").invoke(manager);
        for (Object fragment : fragments) {
            Object child = fragment.getClass().getMethod("getChildFragmentManager").invoke(fragment);
            for (Object item : (List<?>) child.getClass().getMethod("getFragments").invoke(child)) {
                if (item.getClass().getName().matches(".*Fragment(Image|Gif|Video)Preview")) return item;
            }
            if (fragment.getClass().getName().matches(".*Fragment(Image|Gif|Video)Preview")) return fragment;
        }
        return null;
    }

    private static void closePreview() throws Exception {
        onMain(() -> { preview.finish(); return null; });
        preview = null;
        Thread.sleep(100);
    }

    private static void verifyGallery(boolean pro) throws Exception {
        String gif = "https://i.redd.it/PatchedditFixtureGif.gif";
        String jpg = "https://i.redd.it/PatchedditFixtureStill.jpg";
        String mp4 = "https://v.redd.it/fixture/native.mp4";
        String listing = """
                {"kind":"Listing","data":{"children":[{"kind":"t3","data":{
                "id":"PatchedditFixture","name":"t3_PatchedditFixture","author":"fixture","subreddit":"Example",
                "permalink":"/r/Example/comments/PatchedditFixture/gallery/","is_gallery":true,
                "title":"Fixture gallery","url":"https://www.reddit.com/gallery/PatchedditFixture",
                "thumbnail":"https://i.redd.it/PatchedditFixtureGif.gif",
                "gallery_data":{"items":[{"media_id":"PatchedditFixtureGif"},{"media_id":"PatchedditFixtureNative"},
                {"media_id":"PatchedditFixtureStill"}]},
                "media_metadata":{
                "PatchedditFixtureGif":{"status":"failed"},
                "PatchedditFixtureNative":{"status":"valid","m":"image/gif","p":[
                {"u":"https://i.redd.it/native.gif","x":80,"y":40}],
                "s":{"u":"https://i.redd.it/native.gif","mp4":"https://v.redd.it/fixture/native.mp4",
                "gif":"https://i.redd.it/native.gif","x":80,"y":40}},
                "PatchedditFixtureStill":{"status":"unprocessed","m":"image/jpg"}
                }}}]}}
                """;
        var requests = new ArrayList<String>();
        var requestType = type(HTTP + "Request");
        var interceptor = type(HTTP + "Interceptor");
        var chain = type(HTTP + "Interceptor$Chain");
        var bodyType = type(HTTP + "ResponseBody");
        var mediaType = type(HTTP + "MediaType");
        Object fake = Proxy.newProxyInstance(loader, new Class<?>[]{interceptor}, (proxy, method, args) -> {
            Object request = chain.getMethod("request").invoke(args[0]);
            String url = requestType.getMethod("url").invoke(request).toString();
            boolean head = "HEAD".equals(requestType.getMethod("method").invoke(request));
            if (head) {
                check(requestType.getMethod("header", String.class).invoke(request, "Authorization") == null,
                        "Reddit authorization leaked to the gallery CDN");
                requests.add(url);
            }
            Object builder = type(HTTP + "Response$Builder").getConstructor().newInstance();
            builder.getClass().getMethod("request", requestType).invoke(builder, request);
            var protocol = type(HTTP + "Protocol");
            builder.getClass().getMethod("protocol", protocol).invoke(builder, protocol.getField("HTTP_1_1").get(null));
            builder.getClass().getMethod("code", int.class).invoke(builder, !head || url.equals(gif) || url.equals(jpg) ? 200 : 404);
            builder.getClass().getMethod("message", String.class).invoke(builder, "fixture");
            Object mime = mediaType.getMethod("get", String.class).invoke(null, "application/json");
            builder.getClass().getMethod("body", bodyType).invoke(builder,
                    bodyType.getMethod("create", byte[].class, mediaType).invoke(null,
                            head ? new byte[0] : listing.getBytes(StandardCharsets.UTF_8), mime));
            return builder.getClass().getMethod("build").invoke(builder);
        });
        Object builder = type(HTTP + "OkHttpClient$Builder").getConstructor().newInstance();
        Class<?> relay = type("app.morphe.extension.relay.http.OkHttpRequestHook");
        relay.getMethod("init").invoke(null);
        relay.getMethod("installInterceptor", builder.getClass()).invoke(null, builder);
        builder.getClass().getMethod("addInterceptor", interceptor).invoke(builder, fake);
        Object client = builder.getClass().getMethod("build").invoke(builder);
        Object requestBuilder = type(HTTP + "Request$Builder").getConstructor().newInstance();
        requestBuilder.getClass().getMethod("url", String.class).invoke(requestBuilder,
                "https://oauth.reddit.com/api/info?id=t3_PatchedditFixture");
        requestBuilder.getClass().getMethod("header", String.class, String.class).invoke(requestBuilder,
                "Authorization", "Bearer fixture-only");
        Object call = client.getClass().getMethod("newCall", requestType)
                .invoke(client, requestBuilder.getClass().getMethod("build").invoke(requestBuilder));
        String repaired;
        try (AutoCloseable response = (AutoCloseable) type(HTTP + "Call").getMethod("execute").invoke(call)) {
            Object body = type(HTTP + "Response").getMethod("body").invoke(response);
            repaired = (String) bodyType.getMethod("string").invoke(body);
        }
        check(requests.containsAll(List.of(gif, jpg)) && requests.stream().noneMatch(url -> url.contains("Native")),
                "Failed items were not probed, or valid items were probed: " + requests);
        var post = new org.json.JSONObject(repaired).getJSONObject("data").getJSONArray("children").getJSONObject(0);
        ArrayList<?> previews = onMain(() -> {
            var account = type("reddit.news.oauth.reddit.model.RedditAccount");
            Object guest = account.getConstructor().newInstance();
            set(guest, "name", "fixture");
            Object story = type("reddit.news.data.DataStory").getConstructor(org.json.JSONObject.class,
                    android.os.Handler.class, account,
                    SharedPreferences.class).newInstance(post, new android.os.Handler(host.getMainLooper()),
                    guest, preferences);
            return (ArrayList<?>) field(story, pro ? "y0" : "z0");
        });
        check(previews.size() == 3, "Native gallery lost items: " + previews.size());
        check(gif.equals(field(previews.get(0), "mediaUrl")) && (Integer) field(previews.get(0), "type") == 3,
                "Recovered GIF did not become a native GIF preview");
        check(mp4.equals(field(previews.get(1), "mediaUrl")) && (Integer) field(previews.get(1), "type") == 2,
                "Valid native MP4 was changed");
        check(jpg.equals(field(previews.get(2), "mediaUrl")) && (Integer) field(previews.get(2), "type") == 1,
                "Recovered still image regressed");

        onMain(() -> {
            var bus = type("reddit.news.previews.RxBusPreviewIntent");
            var media = type("reddit.news.previews.RxBusPreviewIntent$Media")
                    .getConstructor(String.class, ArrayList.class, boolean.class, int.class)
                    .newInstance("fixture", previews, false, 0);
            bus.getMethod("h", Object.class).invoke(bus.getMethod("d").invoke(null), media);
            return null;
        });
        preview = PatchedditInstrumentation.instance.startActivitySync(new android.content.Intent()
                .setClassName(host, "reddit.news.previews.ActivityPreview")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> onMain(() -> {
            Object fragment = currentFragment();
            if (fragment == null || !fragment.getClass().getName().endsWith("FragmentGifPreview")) return false;
            Object image = field(fragment, "imageView");
            return image instanceof android.widget.ImageView view
                    && view.getDrawable() instanceof android.graphics.drawable.Animatable;
        }), "Native gallery did not decode the recovered GIF");
        closePreview();
    }

    private static void transport(Context context, boolean galleryOnly) throws Exception {
        for (Bitmap.CompressFormat format : new Bitmap.CompressFormat[]{Bitmap.CompressFormat.JPEG,
                Bitmap.CompressFormat.PNG}) {
            Bitmap bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.BLUE);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bitmap.compress(format, 100, bytes);
            bitmap.recycle();
            images.put(format == Bitmap.CompressFormat.JPEG ? "jpeg" : "png", bytes.toByteArray());
        }
        for (String name : new String[]{"inline-animation.gif", "inline-animation.webp", "inline-video.mp4",
                "inline-audio.m4a", "inline-video.ts"}) {
            try (InputStream input = PatchedditInstrumentation.instance.getContext().getAssets().open(name)) {
                images.put(name.substring(name.lastIndexOf('.') + 1), input.readAllBytes());
            }
        }
        Class<?> interceptor = type(HTTP + "Interceptor");
        Object fake = Proxy.newProxyInstance(loader, new Class<?>[]{interceptor}, (proxy, method, args) -> {
            if (method.getName().equals("toString")) return "Relay media fixture";
            Object chain = args[0];
            Object request = chain.getClass().getMethod("request").invoke(chain);
            Object url = request.getClass().getMethod("url").invoke(request);
            String path = (String) url.getClass().getMethod("encodedPath").invoke(url);
            if (!path.contains("PatchedditFixture")) {
                try {
                    return chain.getClass().getMethod("proceed", request.getClass()).invoke(chain, request);
                } catch (java.lang.reflect.InvocationTargetException failure) {
                    throw failure.getCause();
                }
            }
            requests.add(url.toString());
            check(request.getClass().getMethod("header", String.class).invoke(request, "Authorization") == null
                    && request.getClass().getMethod("header", String.class).invoke(request, "Cookie") == null,
                    "Account credentials reached a public media request");
            if (path.endsWith("slow.jpeg")) check(SLOW.await(15, TimeUnit.SECONDS), "Slow fixture timed out");
            if (path.endsWith("stable.jpeg")) {
                check(STABLE_IMAGE.await(15, TimeUnit.SECONDS), "Stable layout fixture timed out");
            }
            byte[] bytes;
            String location = null;
            if (path.endsWith("redirect.mpd") || path.endsWith("outside.mpd")) {
                location = path.endsWith("outside.mpd") ? "https://example.com/manifest.mpd"
                        : "https://vcf.redd.it/PatchedditFixture/moved/DASHPlaylist.mpd" + query;
                bytes = new byte[0];
            } else if (path.endsWith("corrupt.png")) bytes = new byte[]{1, 2, 3};
            else if (path.endsWith("oversized.mpd")) bytes = new byte[513 * 1024];
            else if (path.endsWith("broken.mpd")) bytes = "<broken>".getBytes(StandardCharsets.UTF_8);
            else if (path.endsWith(".mpd")) bytes = ("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" "
                    + "mediaPresentationDuration=\"PT2S\" minBufferTime=\"PT1S\" type=\"static\"><Period>"
                    + "<AdaptationSet mimeType=\"video/mp4\" contentType=\"video\"><Representation id=\"v\" "
                    + "bandwidth=\"100000\" width=\"320\" height=\"180\" codecs=\"avc1.42C01E\">"
                    + "<BaseURL>video" + run + ".mp4</BaseURL></Representation></AdaptationSet>"
                    + "<AdaptationSet mimeType=\"audio/mp4\" contentType=\"audio\"><Representation id=\"a\" "
                    + "bandwidth=\"64000\" codecs=\"mp4a.40.2\"><BaseURL>audio" + run + ".m4a</BaseURL></Representation>"
                    + "</AdaptationSet></Period></MPD>").getBytes(StandardCharsets.UTF_8);
            else if (path.endsWith(".m3u8")) bytes = ("#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:0\n"
                    + "#EXTINF:2,\nvideo.ts\n#EXT-X-ENDLIST\n").getBytes(StandardCharsets.UTF_8);
            else {
                String suffix = path.substring(path.lastIndexOf('.') + 1);
                if (url.toString().contains("format=webp")) suffix = "webp";
                if (url.toString().contains("format=mp4")) suffix = "mp4";
                bytes = images.get(suffix);
            }
            check(bytes != null, "Unexpected fixture request: " + url);
            Object body = type(HTTP + "ResponseBody").getMethod("create", byte[].class, type(HTTP + "MediaType"))
                    .invoke(null, bytes, null);
            Object response = type(HTTP + "Response$Builder").getConstructor().newInstance();
            response.getClass().getMethod("request", request.getClass()).invoke(response, request);
            response.getClass().getMethod("protocol", type(HTTP + "Protocol"))
                    .invoke(response, type(HTTP + "Protocol").getField("HTTP_1_1").get(null));
            response.getClass().getMethod("code", int.class).invoke(response, location == null ? 200 : 302);
            if (location != null) response.getClass().getMethod("header", String.class, String.class)
                    .invoke(response, "Location", location);
            response.getClass().getMethod("message", String.class).invoke(response, "fixture");
            response.getClass().getMethod("body", type(HTTP + "ResponseBody")).invoke(response, body);
            return response.getClass().getMethod("build").invoke(response);
        });
        Context application = context.getApplicationContext();
        Object component = application.getClass().getMethod("b").invoke(application);
        for (Method method : type("reddit.news.oauth.dagger.components.RelayApplicationComponent").getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType().getName().equals(HTTP + "OkHttpClient")) {
                intercept(method.invoke(component), fake);
            }
        }
        if (!galleryOnly) {
            Class<?> client = type("app.morphe.extension.relay.media.MediaViewer$PublicClient");
            Field field = client.getDeclaredField("CLIENT");
            field.setAccessible(true);
            intercept(field.get(null), fake);
        }
    }

    private static void intercept(Object client, Object fake) throws Exception {
        List<Object> values = new ArrayList<>((List<?>) client.getClass().getMethod("interceptors").invoke(client));
        values.add(0, fake);
        set(client, "interceptors", java.util.Collections.unmodifiableList(values));
    }
    private static Class<?> type(String name) throws Exception { return loader.loadClass(name); }
    private static void dependency(Object object, Object value) throws Exception {
        typedField(object, value.getClass()).set(object, value);
    }
    private static Field typedField(Object object, Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field field : object.getClass().getDeclaredFields()) {
            if (field.getType() == type) fields.add(field);
        }
        check(fields.size() == 1, "Ambiguous native fixture dependency");
        fields.get(0).setAccessible(true);
        return fields.get(0);
    }
    private static Object field(Object object, String name) throws Exception {
        check(object != null, "Missing native fixture object");
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass()) {
            try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException next) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass()) {
            try { Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(object, value); return; }
            catch (NoSuchFieldException next) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object invoke(Object owner, String field, String method) throws Exception {
        Object object = field(owner, field);
        return object == null ? false : object.getClass().getMethod(method).invoke(object);
    }
    private static <T> T onMain(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        PatchedditInstrumentation.instance.runOnMainSync(task);
        return task.get(15, TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> ready, String error) throws Exception {
        long end = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < end) {
            if (ready.call()) return;
            Thread.sleep(40);
        }
        throw new AssertionError(error);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
