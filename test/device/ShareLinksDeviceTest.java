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
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.HttpsURLConnection;

public final class ShareLinksDeviceTest {
    private static final String WEB = "https://www.reddit.com";
    private static final String OAUTH = "oauth.reddit.com";
    private static final String ROOT = WEB + "/r/Example";
    private static final String POST_PATH = "/r/Example/comments/abc123/example";
    private static final String COMMENT_PATH = POST_PATH + "/def456";
    private static final String IMAGE = "https://i.redd.it/fixtureimage.png";
    private static final String MATRIX = "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/fixtureasset";
    private static final String MATRIX_WEBVIEW = MATRIX + "WebView";
    private static final String BACON = "com.onelouder.baconreader.LinkDispatcher";
    private static final String JOEY = "o.o.joey.Activities.OpenInApp";
    private static final String RIF = "com.andrewshu.android.reddit.MainActivity";
    private static final String INFINITY = "ml.docilealligator.infinityforreddit.activities.LinkResolverActivity";
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
    private volatile String expectedToken;

    private ShareLinksDeviceTest(Context context, String flavor) throws Exception {
        this.context = context;
        this.flavor = flavor;
        String extensionClient = switch (flavor) {
            case "boost", "relay", "syncforreddit" -> flavor;
            case "baconreader", "joey", "rif", "platinum", "infinity" -> null;
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
            check(!openLegacy("https://example.com/r/Example/s/FixturePost"), "Unrelated link intercepted");
        }
        seedNativeToken(TOKEN);
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
            check(List.of(new Exchange("HEAD", expectedToken == null ? "np.reddit.com" : OAUTH,
                    "/r/Example/s/FixtureNp")).equals(exchanges), "Wrong /s/ request host: " + exchanges);
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
            if (expectedToken == null) assertRequests("/r/Example/s/FixtureMissing");
            else assertRequests("/r/Example/s/FixtureMissing", "/r/Example/s/FixtureMissing");

            Uri foreign = Uri.parse(readFallback(follow(ROOT + "/s/FixtureForeign", false)));
            check(("/r/Example/s/FixtureForeign").equals(foreign.getPath())
                            && isFixtureFallback(foreign.toString()), "Followed an untrusted redirect: " + foreign);
            assertRequests("/r/Example/s/FixtureForeign");
            Uri loop = Uri.parse(readFallback(follow(ROOT + "/s/FixtureLoop", false)));
            check(("/r/Example/s/FixtureLoop").equals(loop.getPath())
                            && isFixtureFallback(loop.toString()), "Did not stop a redirect loop: " + loop);
            assertRequests("/r/Example/s/FixtureLoop");

            if (expectedToken != null) {
                share(ROOT + "/s/FixtureBlockedWeb", POST_PATH, null, false);
                seedNativeToken("morphe-rotated-token");
                share(ROOT + "/s/FixtureBlockedWeb", POST_PATH, null, false);
                assertRequests("/r/Example/s/FixtureBlockedWeb");
            }
            if ("boost".equals(flavor)) verifyBoostExternalShares();
            verifySharedWrappers();
            if ("infinity".equals(flavor)) verifyInfinityGuestShares();
            if ("joey".equals(flavor)) verifyJoey();
            if (rif()) verifyRif();
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    private void seedNativeToken(String accessToken) throws Exception {
        onMain(() -> {
            ClassLoader loader = context.getClassLoader();
            if (rif()) {
                Class<?> preferences = loader.loadClass("rif".equals(flavor) ? "k4.v" : "l4.v");
                Object settings = preferences.getMethod("C").invoke(null);
                field(preferences, "Z0").set(settings, null);
                field(preferences, "e1").set(settings, accessToken);
                field(preferences, "f1").setLong(settings, Long.MAX_VALUE);
            } else if ("boost".equals(flavor)) {
                Class<?> sessionType = loader.loadClass("xd2");
                sessionType.getMethod("D").invoke(null);
                Object session = field(sessionType, "r").get(null);
                Object client = field(sessionType, "e").get(session);
                Class<?> clientType = loader.loadClass("net.dean.jraw.RedditClient");
                Class<?> authType = loader.loadClass("net.dean.jraw.http.AuthenticationMethod");
                Object scheme = authType.getField("USERLESS_APP").get(null);
                Class<?> json = loader.loadClass("com.fasterxml.jackson.databind.JsonNode");
                Class<?> oauth = loader.loadClass("net.dean.jraw.http.oauth.OAuthData");
                Object auth = oauth.getConstructor(authType, json).newInstance(scheme, tokenJson(loader, accessToken));
                field(clientType, "authData").set(client, auth);
                field(clientType, "authMethod").set(client, scheme);
                field(sessionType, "f").set(session, new Date(Long.MAX_VALUE));
            } else if ("syncforreddit".equals(flavor)) {
                seedSyncToken(loader, accessToken);
            } else if ("joey".equals(flavor)) {
                Class<?> singleton = loader.loadClass("d9.c");
                Object session = singleton.getMethod("q").invoke(null);
                Object client = singleton.getMethod("v").invoke(session);
                Class<?> scheme = loader.loadClass("net.dean.jraw.http.a");
                Class<?> json = loader.loadClass("com.fasterxml.jackson.databind.JsonNode");
                Class<?> oauth = loader.loadClass("net.dean.jraw.http.oauth.OAuthData");
                Object auth = oauth.getConstructor(scheme, json)
                        .newInstance(field(scheme, "h").get(null), tokenJson(loader, accessToken));
                field(loader.loadClass("q7.e"), "n").set(client, auth);
            } else if ("baconreader".equals(flavor)) {
                Class<?> manager = loader.loadClass("com.onelouder.baconreader.data.SessionManager");
                Class<?> session = loader.loadClass("com.onelouder.baconreader.data.Session");
                Object selected = session.getConstructor().newInstance();
                field(session, "name").set(selected, "Patcheddit fixture");
                field(session, "accessToken").set(selected, accessToken);
                field(session, "expires").setLong(selected, Long.MAX_VALUE);
                field(manager, "sessions").set(null, new ArrayList<>(List.of(selected)));
                field(manager, "current").set(null, selected);
            } else if ("infinity".equals(flavor)) {
                context.getSharedPreferences("ml.docilealligator.infinityforreddit.current_account", Context.MODE_PRIVATE)
                        .edit().putString("account_name", "Fixture").putString("access_token", accessToken).commit();
            } else {
                Object app = context.getApplicationContext();
                Object component = app.getClass().getMethod("b").invoke(app);
                Class<?> managerType = loader.loadClass("reddit.news.oauth.RedditAccountManager");
                Class<?> componentType = loader.loadClass("reddit.news.oauth.dagger.components.RelayApplicationComponent");
                Method selectedManager = List.of(componentType.getMethods()).stream()
                        .filter(method -> method.getParameterCount() == 0 && method.getReturnType() == managerType)
                        .filter(method -> method.getName().equals("a") || method.getName().equals("d"))
                        .findFirst().orElseThrow();
                Object manager = selectedManager.invoke(component);
                Class<?> account = loader.loadClass("reddit.news.oauth.reddit.model.RedditAccount");
                Class<?> token = loader.loadClass("reddit.news.oauth.reddit.model.RedditAccessToken");
                Object selected = account.getConstructor().newInstance();
                Object auth = token.getConstructor().newInstance();
                field(token, "accessToken").set(auth, accessToken);
                field(token, "refreshToken").set(auth, "fixture-not-an-access-token");
                field(token, "expireTime").setLong(auth, Long.MAX_VALUE);
                field(account, "accessToken").set(selected, auth);
                field(managerType, "b").set(manager, new ArrayList<>(List.of(selected)));
                field(managerType, "a").setInt(manager, 0);
            }
            expectedToken = accessToken;
        });
    }

    private void seedSyncToken(ClassLoader loader, String accessToken) throws Exception {
        Class<?> account = loader.loadClass("com.laurencedawson.reddit_sync.singleton.a");
        Object singleton = account.getMethod("d").invoke(null);
        Field refresh = field(account, "b");
        String oldRefresh = (String) refresh.get(singleton);
        String newRefresh = accessToken + "-refresh";
        refresh.set(singleton, newRefresh);
        Class<?> base = loader.loadClass("x8.a");
        Class<?> authCode = loader.loadClass("b9.a");
        Object chained = authCode.getConstructor(Context.class, String.class,
                        loader.loadClass("com.android.volley.Response$Listener"),
                        loader.loadClass("com.android.volley.Response$ErrorListener"))
                .newInstance(context, "fixture", null, null);
        Class<?> requestType = loader.loadClass("b9.d");
        Object request = requestType.getConstructor(Context.class, base).newInstance(context, chained);
        Method getCacheKey = requestType.getMethod("getCacheKey");
        String key = (String) getCacheKey.invoke(request);
        refresh.set(singleton, oldRefresh);
        check(key.equals(getCacheKey.invoke(request)), "Sync changed the key of a queued request");
        byte[] body = (byte[]) requestType.getMethod("getBody").invoke(request);
        check(new String(body, StandardCharsets.UTF_8).endsWith("=" + newRefresh),
                "Sync changed the credentials of a queued request");
        refresh.set(singleton, newRefresh);

        Class<?> queueType = loader.loadClass("com.android.volley.RequestQueue");
        Class<?> application = loader.loadClass("com.laurencedawson.reddit_sync.RedditApplication");
        Object cache = queueType.getMethod("getCache").invoke(field(application, "b").get(null));
        Class<?> cacheType = loader.loadClass("com.android.volley.Cache");
        Class<?> entryType = loader.loadClass("com.android.volley.Cache$Entry");
        Object entry = entryType.getConstructor().newInstance();
        field(entryType, "data").set(entry, ("{\"access_token\":\"" + accessToken + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        field(entryType, "ttl").setLong(entry, Long.MAX_VALUE);
        cacheType.getMethod("put", String.class, entryType).invoke(cache, key, entry);
    }

    private static Object tokenJson(ClassLoader loader, String accessToken) throws Exception {
        Class<?> mapper = loader.loadClass("com.fasterxml.jackson.databind.ObjectMapper");
        return mapper.getMethod("readTree", String.class).invoke(mapper.getConstructor().newInstance(),
                "{\"access_token\":\"" + accessToken + "\"}");
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
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
        check(List.of(new Exchange("HEAD", OAUTH, "/r/Example/s/FixtureEmbeddedTarget"))
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

    private void verifyRif() throws Exception {
        String imageWrapper = WEB + "/media?url=" + Uri.encode(IMAGE);
        assertExternalRoute(imageWrapper, RIF);
        assertExternalRoute("https://np.reddit.com/media/?url=" + Uri.encode(IMAGE), RIF);
        assertExternalRoute(MATRIX, RIF);
        assertImage(follow(imageWrapper, true));
        check(exchanges.isEmpty(), "Unwrapping a direct image made a network request");
        assertImage(follow(MATRIX, true));
        check(List.of(new Exchange("HEAD", "matrix.redditspace.com", Uri.parse(MATRIX).getPath()))
                .equals(exchanges), "Unexpected Matrix lookup: " + exchanges);
        assertImage(follow(WEB + "/media?url=" + Uri.encode(MATRIX), true));
        check(exchanges.isEmpty(), "RIF repeated a successful public Matrix lookup");

        assertImage(followRifWebView(imageWrapper));
        check(exchanges.isEmpty(), "RIF's WebView fetched the direct image wrapper");
        assertImage(followRifWebView(MATRIX_WEBVIEW));
        check(List.of(new Exchange("HEAD", "matrix.redditspace.com", Uri.parse(MATRIX_WEBVIEW).getPath()))
                .equals(exchanges), "Unexpected Matrix lookup from RIF WebView: " + exchanges);
    }

    private void assertExternalRoute(String link, String expectedActivity) {
        Intent external = new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.getPackageName());
        check(context.getPackageManager().queryIntentActivities(external, 0).stream()
                        .anyMatch(target -> expectedActivity.equals(target.activityInfo.name)),
                "No external link route to " + expectedActivity + " for " + link);
    }

    private void verifyBoostExternalShares() throws Exception {
        String post = ROOT + "/s/FixtureBlockedWeb";
        assertExternalRoute(post, "com.rubenmayayo.reddit.ui.activities.DeepLinkingActivity");
        assertPost(followExternal(post), POST_PATH, null);
        assertRequests("/r/Example/s/FixtureBlockedWeb");

        String missing = ROOT + "/s/FixtureMissing";
        check((missing + "#bypass").equals(readFallback(followExternal(missing))),
                "Boost external share did not fall back to the original link");
        assertRequests("/r/Example/s/FixtureMissing", "/r/Example/s/FixtureMissing");
    }

    @SuppressWarnings("unchecked")
    private void verifyInfinityGuestShares() throws Exception {
        Class<?> session = context.getClassLoader().loadClass("app.morphe.extension.infinity.http.ShareLinkSession");
        Object guestProvider = field(session, "guestProvider").get(null);
        check(guestProvider != null, "Infinity did not initialize its guest OAuth provider");
        Field cache = Arrays.stream(guestProvider.getClass().getDeclaredFields())
                .filter(candidate -> candidate.getType() == AtomicReference.class).findFirst().orElseThrow();
        cache.setAccessible(true);
        ((AtomicReference<String>) cache.get(guestProvider)).set("fixture-guest-token");
        // While signed in, the populated guest cache must not take precedence.
        String link = ROOT + "/s/FixtureBlockedWeb";
        assertPost(followExternal(link), POST_PATH, null);
        assertRequests("/r/Example/s/FixtureBlockedWeb");

        context.getSharedPreferences("ml.docilealligator.infinityforreddit.current_account", Context.MODE_PRIVATE)
                .edit().clear().commit();
        expectedToken = "fixture-guest-token";
        assertPost(followExternal(link), POST_PATH, null);
        assertRequests("/r/Example/s/FixtureBlockedWeb");
    }

    private Intent followExternal(String link) throws Exception {
        resetHandoff();
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return awaitHandoff(link);
    }

    private void verifySharedWrappers() throws Exception {
        String mail = "https://click.redditmail.com/CL0/"
                + Uri.encode(ROOT + "/s/FixtureBlockedWeb") + "/1";
        String appLink = "https://reddit.onelink.me/FixtureWrappedShare";
        String target = switch (flavor) {
            case "boost" -> "com.rubenmayayo.reddit.ui.activities.DeepLinkingActivity";
            case "relay" -> "reddit.news.IntentFilterActivity";
            case "syncforreddit" -> "com.laurencedawson.reddit_sync.ui.activities.IntentActivity";
            case "baconreader" -> BACON;
            case "joey" -> JOEY;
            case "infinity" -> INFINITY;
            case "rif", "platinum" -> "com.andrewshu.android.reddit.intentfilter.RedditMailClickDummyActivity";
            default -> throw new AssertionError(flavor);
        };
        assertExternalRoute(mail, target);
        assertExternalRoute(appLink, rif()
                ? "com.andrewshu.android.reddit.intentfilter.RedditTrackingLinkDummyActivity" : target);
        for (String wrapper : List.of(mail, appLink)) {
            Intent handoff = followExternal(wrapper);
            if (rif()) {
                check(handoff.getDataString() != null && Uri.parse(handoff.getDataString()).getPath().startsWith(POST_PATH),
                        "RIF did not replay the resolved post");
            } else {
                assertPost(handoff, POST_PATH, null);
            }
            if (wrapper.equals(mail)) assertRequests("/r/Example/s/FixtureBlockedWeb");
            else assertRequests("/FixtureWrappedShare", "/r/Example/s/FixtureBlockedWeb");
        }
        share(appLink, POST_PATH, null, !flavor.equals("infinity"));
        assertRequests("/FixtureWrappedShare", "/r/Example/s/FixtureBlockedWeb");
    }

    private Intent followRifWebView(String link) throws Exception {
        resetHandoff();
        Class<?> type = context.getClassLoader().loadClass("rif".equals(flavor) ? "v1.z" : "w1.z");
        var view = new AtomicReference<WebView>();
        try {
            onMain(() -> {
                WebView webView = new WebView(context);
                view.set(webView);
                WebViewClient client = (WebViewClient) type.getConstructor(Context.class).newInstance(context);
                webView.setWebViewClient(client);
                check(client.shouldOverrideUrlLoading(webView, link), "RIF's WebView did not handle " + link);
            });
            return awaitHandoff(link);
        } finally {
            if (view.get() != null) onMain(() -> view.get().destroy());
        }
    }

    private void share(String input, String path, String depth, boolean inApp) throws Exception {
        assertPost(follow(input, inApp), path, depth);
    }

    private Intent follow(String link, boolean inApp) throws Exception {
        // Native Joey startup may refresh its guest session between activity launches.
        if ("joey".equals(flavor)) seedNativeToken(expectedToken);
        resetHandoff();
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
            String activity = switch (flavor) {
                case "baconreader" -> BACON;
                case "infinity" -> INFINITY;
                default -> JOEY;
            };
            Intent input = new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    .setClassName(context.getPackageName(), activity);
            if (inApp) {
                Class<?> type = context.getClassLoader().loadClass("com.onelouder.baconreader.LinkProcessor");
                Object processor = type.getConstructor(Context.class, String.class).newInstance(context, link);
                input = (Intent) type.getMethod("getIntent").invoke(processor);
                check(input.getComponent() != null && BACON.equals(input.getComponent().getClassName()),
                        "BaconReader in-app link bypassed the native dispatcher");
            }
            context.startActivity(input.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return awaitHandoff(link);
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

    private static void assertImage(Intent intent) {
        check(intent.getComponent() != null && RIF.equals(intent.getComponent().getClassName())
                        && Intent.ACTION_VIEW.equals(intent.getAction()),
                "RIF image did not reach the stock browser entry point: " + intent);
        check(IMAGE.equals(intent.getDataString()),
                "RIF lost the unwrapped native image: " + intent);
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
                || data.equals(IMAGE) || data.contains("/gallery/abc123"))
                || "abc123".equals(intent.getStringExtra("submission"))
                || hasExtra(intent, IMAGE) || isFixtureFallback(intent.getStringExtra("url"))
                || isFixtureFallback(intent.getStringExtra(" Url"));
    }

    private static boolean isFixtureFallback(String url) {
        return url != null && url.contains("/s/Fixture")
                && (url.endsWith("#bypass") || url.endsWith("#morphe-share-fallback"));
    }

    private static boolean hasExtra(Intent intent, String expected) {
        Bundle extras = intent.getExtras();
        if (extras == null) return false;
        for (String key : extras.keySet()) {
            if (extras.get(key) instanceof String value && value.contains(expected)) return true;
        }
        return false;
    }

    private void assertRequests(String... paths) {
        List<String> actual = exchanges.stream().map(Exchange::path).toList();
        check(List.of(paths).equals(actual), "Unexpected HTTP requests: " + exchanges);
    }

    private void interceptHttps() {
        URL.setURLStreamHandlerFactory(protocol -> !"https".equals(protocol) ? null : new URLStreamHandler() {
            @Override protected HttpsURLConnection openConnection(URL url) throws IOException {
                String path = url.getPath();
                if (!path.contains("Fixture") && !url.toString().startsWith(MATRIX)
                        && !url.toString().equals(IMAGE)) {
                    throw new IOException("Not an offline fixture URL");
                }
                return new HttpsURLConnection(url) {
                    @Override public int getResponseCode() {
                        String host = url.getHost().toLowerCase(Locale.ROOT);
                        String auth = getRequestProperty("Authorization");
                        boolean oauth = OAUTH.equals(host);
                        if (Looper.myLooper() == Looper.getMainLooper() || getInstanceFollowRedirects()
                                || (oauth ? expectedToken == null || !("Bearer " + expectedToken).equals(auth)
                                        : auth != null)
                                || getRequestProperty("Cookie") != null) {
                            transportFailure.set("Unsafe fixture request: " + getRequestMethod() + " " + host + path);
                            return 400;
                        }
                        exchanges.add(new Exchange(getRequestMethod(), host, path));
                        if (path.endsWith("/FixtureBlockedWeb") && !oauth) return 403;
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
            case "fixtureasset", "fixtureassetWebView" -> new Reply(IMAGE, 302);
            case "FixtureMissing" -> new Reply(null, 404);
            case "FixtureForeign" -> new Reply("https://" + FOREIGN_HOST + "/r/Example/s/FixtureOutside", 302);
            case "FixtureLoop" -> new Reply(path, 302);
            case "FixtureWrapper", "FixtureInApp" -> new Reply(WEB + POST_PATH, 302);
            case "FixtureWrappedShare" -> new Reply(ROOT + "/s/FixtureBlockedWeb", 302);
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
