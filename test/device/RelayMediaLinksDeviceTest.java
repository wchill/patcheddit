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
            transport(context);
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

    private static void transport(Context context) throws Exception {
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
                return chain.getClass().getMethod("proceed", request.getClass()).invoke(chain, request);
            }
            requests.add(url.toString());
            check(request.getClass().getMethod("header", String.class).invoke(request, "Authorization") == null
                    && request.getClass().getMethod("header", String.class).invoke(request, "Cookie") == null,
                    "Account credentials reached a public media request");
            if (path.endsWith("slow.jpeg")) check(SLOW.await(15, TimeUnit.SECONDS), "Slow fixture timed out");
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
        Class<?> client = type("app.morphe.extension.relay.media.MediaViewer$PublicClient");
        Field field = client.getDeclaredField("CLIENT");
        field.setAccessible(true);
        intercept(field.get(null), fake);
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
