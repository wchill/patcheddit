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
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class RedgifsPlaybackDeviceTest {
    private static final String HTTP = "app.morphe.extension.shared.http.libraries.okhttp3.";
    private static final String BACON = "com.onelouder.baconreader.";
    private static final String KEY = "morphe_patch_fix_redgifs_api";
    private static final String WATCH = "https://www.redgifs.com/watch/";
    private static final String CDN = "https://thumbs4.redgifs.com/";
    private static final String PREVIEW = "https://v.redd.it/fixture/preview.gif";
    private static final String TOKEN = "patcheddit-fixture-only";

    private final Context context;
    private final SharedPreferences preferences;
    private final byte[] withAudio;
    private final byte[] silent;
    private final List<String> clipRequests = new CopyOnWriteArrayList<>();
    private final List<String> mediaRequests = new CopyOnWriteArrayList<>();
    private final String runId = "fixture" + Long.toString(System.nanoTime(), 36);

    public static void run(Context context, Bundle arguments) throws Exception {
        new RedgifsPlaybackDeviceTest(context).run(arguments.getString("stock"));
    }

    private RedgifsPlaybackDeviceTest(Context context) throws Exception {
        this.context = context;
        Class<?> settings = type("app.morphe.extension.shared.settings.PatchSettings");
        preferences = (SharedPreferences) settings.getMethod("preferences", Context.class).invoke(null, context);
        withAudio = asset("redgifs-clip.mp4");
        silent = asset("inline-video.mp4");
    }

    private void run(String stock) throws Exception {
        seedToken("Joey");
        seedToken("BaconReader");
        boolean hadSetting = preferences.contains(KEY);
        boolean previous = preferences.getBoolean(KEY, true);
        try {
            enabled(true);
            if ("baconreader".equals(stock)) bacon();
            else if ("joey".equals(stock)) joey();
            else throw new AssertionError("Specify stock=baconreader or stock=joey");
        } finally {
            SharedPreferences.Editor restore = preferences.edit();
            check((hadSetting ? restore.putBoolean(KEY, previous) : restore.remove(KEY)).commit(), "restore switch");
        }
    }

    private void bacon() throws Exception {
        installBaconMediaTransport();
        Class<?> manager = type(BACON + "media.gfycat.RedGifsManager");
        Object service = field(manager, "redGifsApiService").get(null);
        Object proxy = Proxy.getInvocationHandler(service);
        Object retrofit = fieldOfType(proxy, type("retrofit2.Retrofit"));
        Object client = type("retrofit2.Retrofit").getMethod("callFactory").invoke(retrofit);
        installTransport(client);

        String hd = runId + "hd";
        baconPost(WATCH + hd, null, PREVIEW, stream(hd, ".mp4"), true);
        String sd = runId + "sd";
        baconPost("https://example.invalid/original", WATCH + sd, PREVIEW, stream(sd, "-mobile.mp4"), false);
        String direct = runId + "mixed";
        baconPost(stream(direct, "-silent.mp4"), null, null, stream(direct, "-mobile.mp4"), false);
        baconPost("https://example.invalid/original", null, PREVIEW, PREVIEW, false);
        check(clipRequests.containsAll(List.of(hd, sd, direct)), "BaconReader bypassed metadata: " + clipRequests);

        enabled(false);
        String disabled = runId + "off";
        baconPost(stream(disabled, "-silent.mp4"), null, null, stream(disabled, "-silent.mp4"), false);
        check(!clipRequests.contains(disabled), "BaconReader used Redgifs API with the switch off");
    }

    private void baconPost(String url, String override, String preview, String expected, boolean play)
            throws Exception {
        // Start on a local blank document so the fixture transport can be installed before loading media.
        Intent intent = new Intent().setClassName(context, BACON + "BrowserActivity").setData(Uri.parse("about:blank"));
        Activity activity = launch(intent);
        try {
            Object handler = onMain(() -> {
                Object media = field(activity.getClass(), "mediaHandler").get(activity);
                Object link = field(media.getClass(), "link").get(media);
                put(link, "url", url);
                put(link, "urlOverriddenByDest", override);
                if (preview != null) {
                    Object source = baconModel("RedditPreviewSource");
                    put(source, "url", preview);
                    Object variant = baconModel("RedditPreviewVariantItem");
                    put(variant, "source", source);
                    Object variants = baconModel("RedditPreviewVariants");
                    put(variants, "gif", variant);
                    Object image = baconModel("RedditPreviewImage");
                    put(image, "variants", variants);
                    Object previews = baconModel("RedditPreview");
                    put(previews, "images", List.of(image));
                    put(link, "preview", previews);
                }
                media.getClass().getMethod("start").invoke(media);
                return media;
            });
            waitFor(() -> expected.equals(field(handler.getClass(), "resolvedUrl").get(handler)),
                    "BaconReader did not select " + expected);
            if (play) {
                Object video = field(handler.getClass(), "videoHandler").get(handler);
                waitFor(() -> {
                    Object player = video.getClass().getMethod("getPlayer").invoke(video);
                    if (player == null) return false;
                    Object tracks = player.getClass().getMethod("getCurrentTrackSelections").invoke(player);
                    int count = field(tracks.getClass(), "length").getInt(tracks);
                    boolean audio = false;
                    for (int i = 0; i < count; i++) {
                        Object selection = tracks.getClass().getMethod("get", int.class).invoke(tracks, i);
                        if (selection == null) continue;
                        Object format = type("com.google.android.exoplayer2.trackselection.TrackSelection")
                                .getMethod("getSelectedFormat").invoke(selection);
                        String mime = (String) field(format.getClass(), "sampleMimeType").get(format);
                        audio |= mime != null && mime.startsWith("audio/");
                    }
                    return audio && (Long) player.getClass().getMethod("getCurrentPosition").invoke(player) > 250;
                }, "BaconReader's native ExoPlayer did not play the AAC track");
            }
        } finally {
            finish(activity);
        }
    }

    private void joey() throws Exception {
        Class<?> io = type("yd.r1");
        installTransport(method(io, "k").invoke(null));
        Class<?> resolver = type("nd.b");
        Object redgifs = method(resolver, "j").invoke(null);
        SharedPreferences auth = (SharedPreferences) field(resolver, "b").get(redgifs);
        auth.edit().putString("access_token", TOKEN)
                .putLong("access_token_expiry", System.currentTimeMillis() + 3_600_000).commit();

        String hd = runId + "hd";
        String sd = runId + "sd";
        String mixed = runId + "mixed";
        String cached = runId + "retry";
        String staySilent = runId + "silent";
        check(joeyPreview(WATCH + hd) == null, "Joey chose Reddit HLS over Redgifs");
        check(PREVIEW.equals(joeyPreview("https://v.redd.it/fixture")), "Joey changed the Reddit preview");
        check(stream(hd, ".mp4").equals(joeyResolve(WATCH + hd)), "Joey did not choose standard HD");
        check(stream(sd, "-mobile.mp4").equals(joeyResolve(WATCH + sd)), "Joey did not choose SD-only");
        check(stream(mixed, "-mobile.mp4").equals(joeyResolve(stream(mixed, "-silent.mp4"))),
                "Joey treated a silent Redgifs CDN link as a direct video");
        check(stream(cached, ".mp4").equals(joeyResolve(WATCH + cached)), "Joey did not refresh silent metadata");
        check(clipRequests.stream().filter(cached::equals).count() == 2, "Joey's refresh was not bounded");
        check(stream(staySilent, "-silent.mp4").equals(joeyResolve(WATCH + staySilent)),
                "Joey did not keep Redgifs' only usable stream");
        String unavailable = runId + "unavailable";
        String silentLink = stream(unavailable, "-silent.mp4");
        check(silentLink.equals(joeyResolve(silentLink)), "Joey dropped the direct clip when metadata failed");
        String oldUrl = WATCH + runId + "cache";
        cacheJoeyPreview(oldUrl);
        check(!(Boolean) method(type("za.l"), "j", String.class, boolean.class).invoke(null, oldUrl, false),
                "Joey accepted a cached Reddit preview for a Redgifs post");

        enabled(false);
        String disabled = runId + "off";
        check(PREVIEW.equals(joeyPreview(WATCH + disabled)), "Joey ignored the Morphe switch for previews");
        check(stream(disabled, "-silent.mp4").equals(joeyResolve(stream(disabled, "-silent.mp4"))),
                "Joey ignored the Morphe switch for direct URLs");
        check(!clipRequests.contains(disabled), "Joey used Redgifs API with the switch off");
        check((Boolean) method(type("za.l"), "j", String.class, boolean.class).invoke(null, oldUrl, false),
                "Joey changed the native cache check with the switch off");
        enabled(true);
        String playback = runId + "play";
        String selected = joeyResolve(WATCH + playback, true);
        check(stream(playback, ".mp4").equals(selected), "Joey did not download the audio-capable stream");
        verifyJoeyPlayback(selected);
    }

    private void cacheJoeyPreview(String url) throws Exception {
        Class<?> owner = type("za.l");
        for (String name : List.of("j", "k")) {
            @SuppressWarnings("unchecked")
            var cache = (Map<String, String>) field(owner, name).get(null);
            cache.put(url, PREVIEW);
        }
    }

    private String joeyPreview(String url) throws Exception {
        Object mapper = type("com.fasterxml.jackson.databind.ObjectMapper").getConstructor().newInstance();
        Object json = mapper.getClass().getMethod("readTree", String.class).invoke(mapper, new JSONObject()
                .put("url", url).put("preview", new JSONObject().put("reddit_video_preview",
                        new JSONObject().put("hls_url", PREVIEW))).toString());
        Class<?> post = type("net.dean.jraw.models.Submission");
        Object submission = post.getConstructor(type("com.fasterxml.jackson.databind.JsonNode")).newInstance(json);
        return (String) method(type("yd.e0"), "f", post).invoke(null, submission);
    }

    private String joeyResolve(String url) throws Exception {
        return joeyResolve(url, false);
    }

    private String joeyResolve(String url, boolean download) throws Exception {
        Class<?> owner = type("za.l");
        Class<?> callback = type("za.l$f");
        var constructor = owner.getDeclaredConstructor(String.class, String.class, type("za.l$g"), boolean.class);
        constructor.setAccessible(true);
        Object player = constructor.newInstance(url, url, null, false);
        if (download) method(owner, "M", boolean.class).invoke(player, true);
        var taskConstructor = callback.getDeclaredConstructor(owner);
        taskConstructor.setAccessible(true);
        Object task = taskConstructor.newInstance(player);
        return (String) method(owner, "k", String.class, boolean.class, callback, boolean.class)
                .invoke(player, url, false, task, false);
    }

    private void verifyJoeyPlayback(String selected) throws Exception {
        File cached = (File) method(type("za.k"), "b", String.class).invoke(null, selected);
        check(cached != null && cached.length() == withAudio.length,
                "Joey did not cache the selected Redgifs file (HTTP request: " + mediaRequests.contains(selected)
                        + ", bytes: " + (cached == null ? "missing" : cached.length()) + ")");
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        check(launch != null, "No Joey activity to host its native player");
        Activity activity = launch(launch);
        try {
            Class<?> controller = type("za.c");
            Object player = onMain(() -> {
                Object controls = controller.getConstructor(boolean.class).newInstance(false);
                View surface = (View) type("o.o.joey.CustomViews.ZSimpleExoplayerView")
                        .getConstructor(Context.class).newInstance(activity);
                activity.setContentView(surface);
                put(controls, "i", surface);
                put(controls, "f", new View(activity));
                method(controller, "e1", boolean.class, Uri.class, boolean.class)
                        .invoke(controls, false, Uri.fromFile(cached), true);
                Object exo = field(controller, "j").get(controls);
                method(exo.getClass(), "q", boolean.class).invoke(exo, true);
                return exo;
            });
            waitFor(() -> {
                Object format = method(player.getClass(), "d").invoke(player);
                return format != null && format.toString().contains("audio/mp4a-latm")
                        && (Long) player.getClass().getMethod("getCurrentPosition").invoke(player) > 250;
            }, "Joey's native ExoPlayer did not play the AAC track");
            onMain(() -> {
                method(player.getClass(), "q", boolean.class).invoke(player, false);
                return null;
            });
        } finally {
            finish(activity);
        }
    }

    private void seedToken(String agent) throws Exception {
        Class<?> manager = type("app.morphe.extension.shared.fixes.redgifs.RedgifsTokenManager");
        Object token = type(manager.getName() + "$RedgifsToken").getConstructor(String.class, long.class)
                .newInstance(TOKEN, System.currentTimeMillis() / 1000);
        @SuppressWarnings("unchecked")
        var tokens = (Map<String, Object>) field(manager, "tokenMap").get(null);
        synchronized (tokens) {
            tokens.put(agent, token);
        }
    }

    // BaconReader's native ExoPlayer uses HttpURLConnection rather than the metadata client.
    private void installBaconMediaTransport() {
        URL.setURLStreamHandlerFactory(protocol -> !"https".equals(protocol) ? null : new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) throws IOException {
                byte[] bytes;
                if (url.toString().startsWith(CDN + runId)) {
                    bytes = url.getPath().endsWith("-silent.mp4") ? silent : withAudio;
                } else {
                    throw new IOException("Unexpected fixture request to " + url.getHost() + url.getPath());
                }
                return new HttpURLConnection(url) {
                    @Override public void connect() { connected = true; }
                    @Override public void disconnect() { connected = false; }
                    @Override public boolean usingProxy() { return false; }
                    @Override public int getResponseCode() { return HTTP_OK; }
                    @Override public String getResponseMessage() { return "fixture"; }
                    @Override public long getContentLengthLong() { return bytes.length; }
                    @Override public String getHeaderField(String name) {
                        if ("Content-Length".equalsIgnoreCase(name)) return Integer.toString(bytes.length);
                        return "Content-Type".equalsIgnoreCase(name) ? "video/mp4" : null;
                    }
                    @Override public Map<String, List<String>> getHeaderFields() {
                        return Map.of("Content-Type", List.of("video/mp4"),
                                "Content-Length", List.of(Integer.toString(bytes.length)));
                    }
                    @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
                };
            }
        });
    }

    private void installTransport(Object client) throws Exception {
        List<Object> interceptors = new ArrayList<>((List<?>) client.getClass().getMethod("interceptors").invoke(client));
        interceptors.add(transport()); // Leave the real Redgifs auth and response interceptor in front.
        put(client, "interceptors", List.copyOf(interceptors));
    }

    private Object transport() throws Exception {
        Class<?> interceptor = type(HTTP + "Interceptor");
        Class<?> chain = type(HTTP + "Interceptor$Chain");
        Class<?> requestType = type(HTTP + "Request");
        return Proxy.newProxyInstance(context.getClassLoader(), new Class<?>[]{interceptor}, (p, method, args) -> {
            if (method.getName().equals("toString")) return "Redgifs fixture transport";
            Object request = chain.getMethod("request").invoke(args[0]);
            Uri url = Uri.parse(requestType.getMethod("url").invoke(request).toString());
            if ("api.redgifs.com".equals(url.getHost())) {
                String path = url.getPath();
                if (path.equals("/v2/oauth/client")) {
                    return response(request, "{\"access_token\":\"" + TOKEN + "\",\"expires_in\":3600}",
                            "application/json");
                }
                String id = url.getLastPathSegment();
                if (path.equals("/v2/gifs/" + id) && id.startsWith(runId)) {
                    check(("Bearer " + TOKEN).equals(requestType.getMethod("header", String.class)
                            .invoke(request, "Authorization")), "Missing fixture Redgifs authorization");
                    boolean first = !clipRequests.contains(id);
                    clipRequests.add(id);
                    if (id.endsWith("unavailable")) throw new IOException("Fixture metadata unavailable");
                    var urls = new JSONObject();
                    if (id.endsWith("mixed") || id.endsWith("silent") || (id.endsWith("retry") && first)) {
                        urls.put("hd", stream(id, "-silent.mp4"));
                    } else if (!id.endsWith("sd")) {
                        urls.put("hd", stream(id, ".mp4"));
                    }
                    if (id.endsWith("mixed") || id.endsWith("sd")) urls.put("sd", stream(id, "-mobile.mp4"));
                    return response(request, new JSONObject()
                            .put("gif", new JSONObject().put("id", id).put("urls", urls)).toString(), "application/json");
                }
            }
            if ("thumbs4.redgifs.com".equals(url.getHost()) && url.getLastPathSegment().startsWith(runId)) {
                mediaRequests.add(url.toString());
                return response(request, url.getPath().endsWith("-silent.mp4") ? silent : withAudio, "video/mp4");
            }
            throw new IOException("Unexpected fixture request to " + url.getHost() + url.getPath());
        });
    }

    private Object response(Object request, String payload, String contentType) throws Exception {
        return response(request, payload.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private Object response(Object request, byte[] payload, String contentType) throws Exception {
        Class<?> builder = type(HTTP + "Response$Builder");
        Class<?> mime = type(HTTP + "MediaType");
        Class<?> body = type(HTTP + "ResponseBody");
        Class<?> protocol = type(HTTP + "Protocol");
        Object value = builder.getConstructor().newInstance();
        builder.getMethod("request", type(HTTP + "Request")).invoke(value, request);
        builder.getMethod("protocol", protocol).invoke(value, protocol.getField("HTTP_1_1").get(null));
        builder.getMethod("code", int.class).invoke(value, 200);
        builder.getMethod("message", String.class).invoke(value, "fixture");
        builder.getMethod("header", String.class, String.class).invoke(value, "Content-Type", contentType);
        builder.getMethod("header", String.class, String.class)
                .invoke(value, "Content-Length", Integer.toString(payload.length));
        builder.getMethod("body", body).invoke(value, body.getMethod("create", byte[].class, mime)
                .invoke(null, payload, mime.getMethod("get", String.class).invoke(null, contentType)));
        return builder.getMethod("build").invoke(value);
    }

    private static String stream(String id, String suffix) {
        return CDN + id + suffix + "?token=fixture%2B1";
    }

    private Class<?> type(String name) throws Exception {
        return context.getClassLoader().loadClass(name);
    }

    private Object baconModel(String name) throws Exception {
        return type(BACON + "reddit." + name).getConstructor().newInstance();
    }

    private static Object fieldOfType(Object owner, Class<?> type) throws Exception {
        for (Field candidate : owner.getClass().getDeclaredFields()) {
            if (candidate.getType() == type) {
                candidate.setAccessible(true);
                return candidate.get(owner);
            }
        }
        throw new NoSuchFieldException(type.getName());
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void put(Object owner, String name, Object value) throws Exception {
        field(owner.getClass(), name).set(owner, value);
    }

    private static Method method(Class<?> cls, String name, Class<?>... args) throws Exception {
        Method method = cls.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private void enabled(boolean enabled) {
        check(preferences.edit().putBoolean(KEY, enabled).commit(), "set Morphe switch");
    }

    private static Activity launch(Intent intent) {
        return PatchedditInstrumentation.instance.startActivitySync(
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
    }

    private static void finish(Activity activity) {
        PatchedditInstrumentation.instance.runOnMainSync(activity::finish);
        PatchedditInstrumentation.instance.waitForIdleSync();
    }

    private static byte[] asset(String name) throws IOException {
        try (var input = PatchedditInstrumentation.instance.getContext().getAssets().open(name)) {
            return input.readAllBytes();
        }
    }

    private static void waitFor(Callable<Boolean> test, String failure) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 8000;
        do {
            if (onMain(test)) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError(failure);
    }

    private static <T> T onMain(Callable<T> body) throws Exception {
        FutureTask<T> task = new FutureTask<>(body);
        PatchedditInstrumentation.instance.runOnMainSync(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
