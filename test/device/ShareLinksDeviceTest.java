/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLStreamHandler;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.HttpsURLConnection;

public final class ShareLinksDeviceTest {
    private static final String WEB = "https://www.reddit.com";
    private static final String ROOT = WEB + "/r/Example";
    private static final String POST_PATH = "/r/Example/comments/abc123/example";
    private static final String COMMENT_PATH = POST_PATH + "/def456";
    private static final String BACON = "com.onelouder.baconreader.LinkDispatcher";
    private static final String JOEY = "o.o.joey.Activities.OpenInApp";
    private static final String RIF = "com.andrewshu.android.reddit.MainActivity";
    private static final String TOKEN = "morphe-fixture-token";
    private static final String FOREIGN_HOST = "share-fixture.invalid";

    private record Reply(String location, int code) {}
    private record Exchange(String method, String host, String path) {}

    public static void run(Context context, Bundle arguments) throws Exception {
        check(context.getPackageName().startsWith("app.patcheddit.rollout."),
                "Only replace HTTPS transport in a disposable app");
        String flavor = arguments.getString("stock", "");
        var fixture = new ShareLinksDeviceTest(context, flavor);
        fixture.run();
    }

    private final Context context;
    private final String flavor;
    private final Class<?> legacy;
    private final Instrumentation instrumentation = PatchedditInstrumentation.instance;
    private final LinkedBlockingQueue<Intent> handoffs = new LinkedBlockingQueue<>();
    private final AtomicReference<String> transportFailure = new AtomicReference<>();
    private final List<Exchange> exchanges = java.util.Collections.synchronizedList(new ArrayList<>());

    private ShareLinksDeviceTest(Context context, String flavor) throws Exception {
        this.context = context;
        this.flavor = flavor;
        String extensionClient = switch (flavor) {
            case "boost", "relay", "syncforreddit" -> flavor;
            case "baconreader", "joey", "rif", "platinum" -> null;
            default -> throw new AssertionError("Unknown client: " + flavor);
        };
        legacy = extensionClient == null ? null : context.getClassLoader().loadClass(
                "app.morphe.extension." + extensionClient + ".FixSLinksPatch");
    }

    private boolean rif() {
        return "rif".equals(flavor) || "platinum".equals(flavor);
    }

    private void run() throws Exception {
        interceptHttps();
        if (legacy != null) {
            legacy.getMethod("patchSetAccessToken", String.class).invoke(null, TOKEN);
            check(!openLegacy("https://example.com/r/Example/s/FixturePost"), "Unrelated link intercepted");
        }
        var monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                boolean rifReplay = rif() && intent.getComponent() != null
                        && RIF.equals(intent.getComponent().getClassName())
                        && context.getPackageName().equals(intent.getComponent().getPackageName());
                if (!rifReplay && !isFixtureHandoff(intent)) return null;
                handoffs.add(new Intent(intent));
                return new Instrumentation.ActivityResult(Activity.RESULT_OK, null);
            }
        };
        instrumentation.addMonitor(monitor);
        try {
            share(ROOT + "/s/FixturePost?utm_source=ignored", POST_PATH, null, false);
            share(ROOT + "/s/FixtureComment", COMMENT_PATH, "3", "baconreader".equals(flavor));
            share(WEB + "/u/example/s/FixtureUser", POST_PATH, null, false);
            share(WEB + "/user/example/s/FixtureProfile", COMMENT_PATH, "3", false);
            Intent nonParticipating = follow("https://np.reddit.com/r/Example/s/FixtureNp", false);
            assertPost(nonParticipating, COMMENT_PATH, "3");
            check(nonParticipating.getBooleanExtra("np", false)
                            || nonParticipating.getData() != null
                            && "np.reddit.com".equals(nonParticipating.getData().getHost()),
                    "Lost no-participation semantics");
            check(List.of(new Exchange("HEAD", "np.reddit.com", "/r/Example/s/FixtureNp")).equals(exchanges),
                    "Request lost the non-participation host: " + exchanges);
            Intent chained = follow(ROOT + "/s/FixtureChain", false);
            assertPost(chained, POST_PATH, null);
            assertRequests("/r/Example/s/FixtureChain", "/r/Example/s/FixtureHop");

            share(ROOT + "/s/FixtureGet", POST_PATH, null, false);
            assertRequests("/r/Example/s/FixtureGet", "/r/Example/s/FixtureGet");
            check(exchanges.stream().map(Exchange::method).toList().equals(List.of("HEAD", "GET")),
                    "Expected a HEAD-to-GET retry after 405: " + exchanges);

            String missing = ROOT + "/s/FixtureMissing";
            Intent fallback = follow(missing, false);
            Uri fallbackUri = Uri.parse(readFallback(fallback));
            check("/r/Example/s/FixtureMissing".equals(fallbackUri.getPath())
                            && ("bypass".equals(fallbackUri.getFragment())
                            || "morphe-share-fallback".equals(fallbackUri.getFragment())),
                    "Wrong unresolved-link fallback");
            if ("baconreader".equals(flavor)) {
                check(fallback.getBooleanExtra("EXTRA_FORCE_BROWSER", false),
                        "BaconReader lost its browser fallback");
            }
            assertRequests("/r/Example/s/FixtureMissing");

            Uri foreign = Uri.parse(readFallback(follow(ROOT + "/s/FixtureForeign", false)));
            check(("/r/Example/s/FixtureForeign").equals(foreign.getPath())
                            && isFixtureFallback(foreign.toString()), "Followed an untrusted redirect: " + foreign);
            assertRequests("/r/Example/s/FixtureForeign");
            Uri loop = Uri.parse(readFallback(follow(ROOT + "/s/FixtureLoop", false)));
            check(("/r/Example/s/FixtureLoop").equals(loop.getPath())
                            && isFixtureFallback(loop.toString()), "Did not stop a redirect loop: " + loop);
            assertRequests("/r/Example/s/FixtureLoop");

            if ("joey".equals(flavor)) verifyJoey();
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    private void verifyJoey() throws Exception {
        share(WEB + "/s/FixtureBare", POST_PATH, null, false);
        for (String host : List.of("s.reddit.com", "applink.reddit.com", "reddit.onelink.me",
                "c.redditmail.com", "click.redditmail.com")) {
            String wrapper = "https://" + host + "/FixtureWrapper";
            assertExternalRoute(wrapper, JOEY);
            share(wrapper, POST_PATH, null, false);
        }
        share("https://reddit.onelink.me/FixtureGet", POST_PATH, null, false);
        assertRequests("/FixtureGet", "/FixtureGet");
        check("GET".equals(exchanges.get(1).method()), "Joey did not retry GET after HEAD");
        share("https://s.reddit.com/FixtureInApp", POST_PATH, null, true);
        share("https://click.redditmail.com/CL0/" + Uri.encode(ROOT + "/s/FixtureMailEmbedded") + "/1",
                POST_PATH, null, false);
        assertRequests("/r/Example/s/FixtureMailEmbedded");
        share("https://reddit.onelink.me/FixtureEmbedded?deep_link_value="
                + Uri.encode(ROOT + "/s/FixtureEmbeddedTarget"), POST_PATH, null, false);
        check(List.of(new Exchange("HEAD", "www.reddit.com", "/r/Example/s/FixtureEmbeddedTarget"))
                .equals(exchanges), "Joey fetched the wrapper despite a recognized embedded target: " + exchanges);
        Intent gallery = follow(ROOT + "/s/FixtureGallery", false);
        Uri galleryUri = gallery.getData();
        check(galleryUri != null && galleryUri.getPath().contains("/comments/abc123"),
                "Joey did not normalize the gallery permalink: " + gallery);
        assertPost(follow(ROOT + "/s/FixtureAlternateComment", false), COMMENT_PATH, "3");
        Intent directGallery = follow(WEB + "/gallery/abc123", true);
        check(directGallery.getData() != null
                        && "/comments/abc123".equals(directGallery.getData().getPath()),
                "Joey did not route a tapped gallery link to its post: " + directGallery);
        check(exchanges.isEmpty(), "Joey requested a direct gallery link over the network");
        assertPost(follow(WEB + POST_PATH + "?comment=def456&context=3", true), COMMENT_PATH, "3");
        check(exchanges.isEmpty(), "Joey requested a direct comment link over the network");
        Intent profileComment = follow(ROOT + "/s/FixtureProfilePermalink", false);
        check(profileComment.getComponent() != null
                        && "o.o.joey.Activities.CommentActivity".equals(profileComment.getComponent().getClassName())
                        && "abc123".equals(profileComment.getStringExtra("submission"))
                        && "def456".equals(profileComment.getStringExtra("highlight_context"))
                        && "u_example".equals(profileComment.getStringExtra("subreddit"))
                        && profileComment.getIntExtra("EXTRA_HIGHLIGHT_CONTEXT_COUNT", -1) == 3,
                "Joey lost native profile-comment routing");
    }

    private void assertExternalRoute(String link, String expectedActivity) {
        Intent external = new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.getPackageName());
        check(context.getPackageManager().queryIntentActivities(external, 0).stream()
                        .anyMatch(target -> expectedActivity.equals(target.activityInfo.name)),
                "No external link route to " + expectedActivity + " for " + link);
    }

    private void share(String input, String path, String depth, boolean inApp) throws Exception {
        assertPost(follow(input, inApp), path, depth);
    }

    private Intent follow(String link, boolean inApp) throws Exception {
        resetHandoff();
        Activity original = null;
        try {
            if (legacy != null) {
                onMain(() -> openNativeLegacy(link));
            } else if (rif()) {
                onMain(() -> openRifInApp(link));
            } else if ("joey".equals(flavor) && inApp) {
                onMain(() -> {
                    Class<?> router = context.getClassLoader().loadClass("eb.a");
                    Method dispatch = router.getDeclaredMethod("F", Context.class, String.class, String.class,
                            String.class, boolean.class, Boolean.class);
                    dispatch.setAccessible(true);
                    dispatch.invoke(null, context, link, null, null, false, false);
                });
            } else {
                String activity = "baconreader".equals(flavor) ? BACON : JOEY;
                Intent input = new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                        .setClassName(context.getPackageName(), activity);
                if (inApp) {
                    Class<?> type = context.getClassLoader().loadClass("com.onelouder.baconreader.LinkProcessor");
                    Object processor = type.getConstructor(Context.class, String.class).newInstance(context, link);
                    input = (Intent) type.getMethod("getIntent").invoke(processor);
                    check(input.getComponent() != null && BACON.equals(input.getComponent().getClassName()),
                            "BaconReader in-app link bypassed the native dispatcher");
                }
                original = instrumentation.startActivitySync(input.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            }
            return awaitHandoff(link);
        } finally {
            if (original != null) instrumentation.runOnMainSync(original::finish);
        }
    }

    private void resetHandoff() {
        handoffs.clear();
        exchanges.clear();
        transportFailure.set(null);
    }

    private Intent awaitHandoff(String link) throws InterruptedException {
        Intent result = handoffs.poll(5, TimeUnit.SECONDS);
        check(transportFailure.get() == null, transportFailure.get());
        check(result != null, "No native handoff for " + link + "; requests: " + exchanges);
        String owner = result.getComponent() == null ? result.getPackage() : result.getComponent().getPackageName();
        check(context.getPackageName().equals(owner), "Handoff left the test app: " + result);
        return result;
    }

    @FunctionalInterface
    private interface Route {
        void open() throws Exception;
    }

    private void onMain(Route route) throws Exception {
        var call = new FutureTask<Void>(() -> {
            route.open();
            return null;
        });
        instrumentation.runOnMainSync(call);
        call.get(5, TimeUnit.SECONDS);
    }

    private boolean openLegacy(String link) throws Exception {
        return (Boolean) legacy.getMethod("patchResolveSLink", String.class).invoke(null, link);
    }

    private void openNativeLegacy(String link) throws Exception {
        ClassLoader loader = context.getClassLoader();
        switch (flavor) {
            case "boost" -> {
                Method open = loader.loadClass("gz1").getDeclaredMethod("o", Context.class, String.class, boolean.class);
                open.setAccessible(true);
                check((Boolean) open.invoke(null, context, link, false), "Boost did not handle " + link);
            }
            case "syncforreddit" -> {
                Object accounts = Array.newInstance(loader.loadClass("r0.d"), 0);
                Method open = loader.loadClass("y7.b").getDeclaredMethod("c", Context.class, accounts.getClass(),
                        String.class, String.class, String.class, String.class, String.class,
                        boolean.class, boolean.class, boolean.class);
                open.setAccessible(true);
                check((Boolean) open.invoke(null, context, accounts, "", link, "", "", "", false, false, false),
                        "Sync did not handle " + link);
            }
            case "relay" -> {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                Class<?> mediaType = loader.loadClass("reddit.news.previews.MediaUrlFetcher");
                Object[] dependencies = new Object[9];
                dependencies[8] = preferences;
                Object media = mediaType.getConstructors()[0].newInstance(dependencies);
                Class<?> owner = loader.loadClass("reddit.news.utils.UrlLinkClickManager");
                Object router = owner.getConstructor(SharedPreferences.class, mediaType).newInstance(preferences, media);
                owner.getMethod("c", String.class, loader.loadClass("androidx.fragment.app.FragmentActivity"))
                        .invoke(router, link, null);
            }
            default -> throw new AssertionError("Missing native legacy route for " + flavor);
        }
    }

    private void openRifInApp(String link) throws Exception {
        // A renamed RIF APK still looks up the stock app's unexported providers on MainActivity startup.
        Class<?> owner = context.getClassLoader().loadClass("rif".equals(flavor) ? "l3.g" : "m3.g");
        for (Method method : owner.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!"q".equals(method.getName()) || parameters.length != 10
                    || parameters[0] != String.class || parameters[8] != Context.class) continue;
            method.setAccessible(true);
            Object[] args = new Object[10];
            args[0] = link;
            args[5] = false;
            args[8] = context;
            method.invoke(null, args);
            return;
        }
        throw new AssertionError("Missing native RIF link router");
    }

    private void assertPost(Intent intent, String path, String depth) {
        if ("joey".equals(flavor) && depth != null) {
            check(intent.getComponent() != null
                            && "o.o.joey.Activities.CommentActivity".equals(intent.getComponent().getClassName())
                            && "abc123".equals(intent.getStringExtra("submission"))
                            && "def456".equals(intent.getStringExtra("highlight_context"))
                            && "Example".equalsIgnoreCase(intent.getStringExtra("subreddit"))
                            && Integer.parseInt(depth) == intent.getIntExtra("EXTRA_HIGHLIGHT_CONTEXT_COUNT", -1),
                    "Joey lost native comment focus or context");
            return;
        }
        Uri uri = intent.getData();
        check(uri != null && (path.equals(uri.getPath()) || (path + "/").equals(uri.getPath())),
                "Wrong native permalink: " + intent);
        check(uri.getQueryParameter("utm_source") == null && uri.getQueryParameter("share_id") == null,
                "Permalink kept tracking parameters");
        if (depth != null) check(depth.equals(uri.getQueryParameter("context")), "Lost comment context: " + uri);
        if (rif()) check(intent.getComponent() != null && RIF.equals(intent.getComponent().getClassName()),
                "RIF did not return to its native dispatcher");
    }

    private static String readFallback(Intent intent) {
        String url = intent.getDataString();
        if (url == null) url = intent.getStringExtra("url");
        if (url == null) url = intent.getStringExtra(" Url");
        check(url != null, "The browser fallback lost its URL");
        return url;
    }

    private static boolean isFixtureHandoff(Intent intent) {
        String data = intent.getDataString();
        return data != null && (data.contains("/comments/abc123") || isFixtureFallback(data)
                || data.contains("/gallery/abc123"))
                || "abc123".equals(intent.getStringExtra("submission"))
                || isFixtureFallback(intent.getStringExtra("url"))
                || isFixtureFallback(intent.getStringExtra(" Url"));
    }

    private static boolean isFixtureFallback(String url) {
        return url != null && url.contains("/s/Fixture")
                && (url.endsWith("#bypass") || url.endsWith("#morphe-share-fallback"));
    }

    private void assertRequests(String... paths) {
        List<String> actual = exchanges.stream().map(Exchange::path).toList();
        check(List.of(paths).equals(actual), "Unexpected HTTP requests: " + exchanges);
    }

    private void interceptHttps() {
        URL.setURLStreamHandlerFactory(protocol -> !"https".equals(protocol) ? null : new URLStreamHandler() {
            @Override protected HttpsURLConnection openConnection(URL url) throws IOException {
                String path = url.getPath();
                if (!path.contains("Fixture")) {
                    throw new IOException("Not an offline fixture URL");
                }
                return new HttpsURLConnection(url) {
                    @Override public int getResponseCode() {
                        String host = url.getHost().toLowerCase(Locale.ROOT);
                        String auth = getRequestProperty("Authorization");
                        boolean redirect = path.endsWith("/FixtureHop")
                                || path.endsWith("/FixtureEmbeddedTarget")
                                || path.endsWith("/FixtureMailEmbedded");
                        boolean legacyOriginal = legacy != null && path.endsWith("/FixtureChain")
                                && "www.reddit.com".equals(host);
                        if (Looper.myLooper() == Looper.getMainLooper() || getInstanceFollowRedirects()
                                || (auth != null && (redirect || !"www.reddit.com".equals(host)))
                                || (legacyOriginal && !("Bearer " + TOKEN).equals(auth))
                                || getRequestProperty("Cookie") != null) {
                            transportFailure.set("Unsafe fixture request to " + host + path);
                            return 400;
                        }
                        exchanges.add(new Exchange(getRequestMethod(), host, path));
                        return reply(getRequestMethod(), path).code();
                    }

                    @Override public String getHeaderField(String name) {
                        return "Location".equalsIgnoreCase(name) ? reply(getRequestMethod(), path).location() : null;
                    }
                    @Override public void connect() {}
                    @Override public void disconnect() {}
                    @Override public boolean usingProxy() { return false; }
                    @Override public String getCipherSuite() { return "fixture"; }
                    @Override public Certificate[] getLocalCertificates() { return null; }
                    @Override public Certificate[] getServerCertificates() { return null; }
                };
            }
        });
    }

    private static Reply reply(String method, String path) {
        String leaf = path.substring(path.lastIndexOf('/') + 1);
        return switch (leaf) {
            case "FixtureMissing" -> new Reply(null, 404);
            case "FixtureForeign" -> new Reply("https://" + FOREIGN_HOST + "/r/Example/s/FixtureOutside", 302);
            case "FixtureLoop" -> new Reply(path, 302);
            case "FixtureWrapper", "FixtureInApp" -> new Reply(WEB + POST_PATH, 302);
            case "FixtureGet" -> "HEAD".equals(method) ? new Reply(null, 405) : new Reply(WEB + POST_PATH, 302);
            case "FixtureChain" -> new Reply("/r/Example/s/FixtureHop", 307);
            case "FixtureGallery" -> new Reply("/gallery/abc123", 302);
            case "FixtureAlternateComment" -> new Reply(POST_PATH + "?comment=def456&context=3", 302);
            case "FixtureProfilePermalink" ->
                    new Reply("/user/example/comments/abc123/example/def456?context=3", 302);
            case "FixtureComment", "FixtureProfile", "FixtureNp" ->
                    new Reply(COMMENT_PATH + "/?utm_source=ignored&share_id=discard&context=3", 302);
            default -> new Reply(POST_PATH + "/?utm_source=ignored", 302);
        };
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
