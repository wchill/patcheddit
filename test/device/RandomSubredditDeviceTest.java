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
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.ContextThemeWrapper;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

public final class RandomSubredditDeviceTest {
    private static final String SHARED = "app.morphe.extension.shared.";
    private static final String SETTING = "morphe_random_subreddit_provider";
    private static final String SWITCH = "morphe_patch_fix_random_subreddit";
    private static final String EXCEPTION_SWITCH = "morphe_patch_log_exceptions";
    private static final String TOKEN = "Bearer random-fixture-only";
    private static final String RANDOM = "https://oauth.reddit.com/r/random/new.json?limit=1";
    private static final String REQUEST_PREFIX = "https://oauth.reddit.com/r/";
    private final Context context;
    private final List<String> providers = new ArrayList<>();
    private String http;
    private int lookupStatus = 200;

    public static void run(Context context, Bundle arguments) throws Exception {
        new RandomSubredditDeviceTest(context).run(arguments.getString("stock"),
                "true".equals(arguments.getString("verifyExceptionLog")));
    }

    private RandomSubredditDeviceTest(Context context) {
        this.context = context;
    }

    private void run(String stock, boolean verifyExceptionLog) throws Exception {
        check(context.getPackageName().startsWith("app.patcheddit.rollout."), "Requires a disposable APK");
        var prefs = (SharedPreferences) type(SHARED + "settings.PatchSettings")
                .getMethod("preferences", Context.class).invoke(null, context);
        http = "boost".equals(stock) ? "okhttp3." : SHARED + "http.libraries.okhttp3.";
        prefs.edit().remove(SETTING).commit();
        verifyPreferenceXml("boost".equals(stock) ? "morphe_boost_settings_skeleton" : "morphe_settings");
        if ("joey".equals(stock)) verifySettings();
        prefs.edit().putBoolean(SWITCH, true).remove(SETTING).commit();
        Route route = "infinity".equals(stock) ? nativeInfinityRoute() : canonicalRoute();
        check((REQUEST_PREFIX + "FixtureRand/new.json?limit=1").equals(route.send(RANDOM)),
                "Random did not use RedditRand with no provider preference");
        check(providers.equals(List.of("rand", "about")), "Unexpected RedditRand requests: " + providers);
        providers.clear();
        prefs.edit().putString(SETTING, "reddit_search").commit();
        check((REQUEST_PREFIX + "FixtureSearch/new.json?limit=1").equals(route.send(RANDOM)),
                "Random ignored the saved Reddit search preference");
        check(providers.equals(List.of("search")), "Unexpected Reddit search requests: " + providers);
        providers.clear();
        prefs.edit().putBoolean(SWITCH, false).commit();
        check(RANDOM.equals(route.send(RANDOM)) && providers.isEmpty(), "Random switch did not disable the rewrite");
        if (verifyExceptionLog) verifyFailureReport(prefs, route);
        prefs.edit().remove(SETTING).remove(SWITCH).commit();
    }

    private void verifyFailureReport(SharedPreferences prefs, Route route) throws Exception {
        Object store = type(SHARED + "debug.ExceptionHook").getMethod("getLogStore", Context.class)
                .invoke(null, context);
        var read = store.getClass().getMethod("read");
        var clear = store.getClass().getMethod("clear");
        clear.invoke(store);
        lookupStatus = 429;
        try {
            prefs.edit().putBoolean(SWITCH, true).putBoolean(EXCEPTION_SWITCH, true).remove(SETTING).commit();
            verifyLookupFailed(route);
            String log = (String) read.invoke(store);
            String entry = "Context: RandomSubreddit: Random subreddit lookup (RedditRand)";
            check(log.contains("HTTP 429") && log.indexOf(entry) >= 0
                            && log.indexOf(entry) == log.lastIndexOf(entry),
                    "The final Random failure was not recorded exactly once with its status");
            check(!log.contains("fixture-secret-response"), "The log contains a raw response");
            prefs.edit().putBoolean(EXCEPTION_SWITCH, false).commit();
            verifyLookupFailed(route);
            check(log.equals(read.invoke(store)), "A disabled exception log still recorded a lookup");
        } finally {
            lookupStatus = 200;
            prefs.edit().remove(EXCEPTION_SWITCH).commit();
            clear.invoke(store);
        }
    }

    private static void verifyLookupFailed(Route route) throws Exception {
        try {
            String sent = route.send(RANDOM);
            throw new AssertionError("A failed lookup sent a listing request: " + sent);
        } catch (InvocationTargetException wrapped) {
            check(wrapped.getCause() instanceof IOException failure
                            && failure.getMessage().contains("HTTP 429"),
                    "Expected the lookup failure, not " + wrapped.getCause());
        }
    }

    private void verifyPreferenceXml(String resource) throws Exception {
        int id = context.getResources().getIdentifier(resource, "xml", context.getPackageName());
        check(id != 0, "Missing Patcheddit settings XML");
        try (var parser = context.getResources().getXml(id)) {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                String key = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "key");
                if (!SETTING.equals(key)) continue;
                ListPreference preference = (ListPreference) type(parser.getName())
                        .getConstructor(Context.class, AttributeSet.class).newInstance(
                                new ContextThemeWrapper(context, android.R.style.Theme_Material_Light),
                                Xml.asAttributeSet(parser));
                check(SETTING.equals(preference.getKey()) && preference.getEntries().length == 2,
                        "Could not read the two configured providers");
                return;
            }
        }
        throw new AssertionError("Random subreddit source was not included in the settings XML");
    }

    private void verifySettings() throws Exception {
        var instrumentation = PatchedditInstrumentation.instance;
        Activity activity = instrumentation.startActivitySync(new Intent()
                .setClassName(context, SHARED + "settings.MorpheSettingsActivity")
                .putExtra("morphe_screen", "morphe_category_feed")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            FutureTask<Void> verify = new FutureTask<>(() -> {
                var preference = (ListPreference) ((PreferenceActivity) activity).findPreference(SETTING);
                check(preference != null && preference.getEntries().length == 2, "Missing provider choices");
                check("redditrand".equals(preference.getValue()), "Wrong default in Patcheddit settings");
                return null;
            });
            instrumentation.runOnMainSync(verify);
            verify.get(5, TimeUnit.SECONDS);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private Route canonicalRoute() throws Exception {
        Class<?> clientType = type(http + "OkHttpClient");
        Class<?> builderType = type(http + "OkHttpClient$Builder");
        Class<?> requestType = type(http + "Request");
        Class<?> requestBuilder = type(http + "Request$Builder");
        Class<?> lookup = type(SHARED + "fixes.feed.RandomSubredditInterceptor$Transport");
        var lookupClient = lookup.getDeclaredField("CLIENT");
        lookupClient.setAccessible(true);
        var interceptors = clientType.getDeclaredField("interceptors");
        interceptors.setAccessible(true);
        interceptors.set(lookupClient.get(null), List.of(transport(true)));

        Object client = clientType.getConstructor().newInstance();
        check(((List<?>) clientType.getMethod("interceptors").invoke(client)).stream().anyMatch(
                item -> item.getClass().getName().equals(SHARED + "fixes.feed.RandomSubredditInterceptor")),
                "The native HTTP client did not register the Random interceptor");
        Object builder = clientType.getMethod("newBuilder").invoke(client);
        builderType.getMethod("addInterceptor", type(http + "Interceptor")).invoke(builder, transport(false));
        Object testClient = builderType.getMethod("build").invoke(builder);
        return url -> {
            Object request = requestBuilder.getConstructor().newInstance();
            requestBuilder.getMethod("url", String.class).invoke(request, url);
            requestBuilder.getMethod("header", String.class, String.class).invoke(request, "Authorization", TOKEN);
            Object call = clientType.getMethod("newCall", requestType)
                    .invoke(testClient, requestBuilder.getMethod("build").invoke(request));
            Class<?> responseType = type(http + "Response");
            try (AutoCloseable response = (AutoCloseable) type(http + "Call").getMethod("execute").invoke(call)) {
                Object sent = responseType.getMethod("request").invoke(response);
                return requestType.getMethod("url").invoke(sent).toString();
            }
        };
    }

    private Object transport(boolean lookup) throws Exception {
        Class<?> requestType = type(http + "Request");
        Class<?> chain = type(http + "Interceptor$Chain");
        Class<?> builder = type(http + "Response$Builder");
        Class<?> body = type(http + "ResponseBody");
        Class<?> media = type(http + "MediaType");
        Class<?> protocol = type(http + "Protocol");
        return Proxy.newProxyInstance(context.getClassLoader(), new Class<?>[]{type(http + "Interceptor")},
                (proxy, method, args) -> {
                    if (!"intercept".equals(method.getName())) throw new UnsupportedOperationException();
                    Object request = chain.getMethod("request").invoke(args[0]);
                    String payload = !lookup ? "{}" : lookupStatus != 200 ? "fixture-secret-response"
                            : reply(URI.create(requestType.getMethod("url").invoke(request).toString()),
                            (String) requestType.getMethod("header", String.class).invoke(request, "Authorization"));
                    Object response = builder.getConstructor().newInstance();
                    builder.getMethod("request", requestType).invoke(response, request);
                    builder.getMethod("protocol", protocol).invoke(response, protocol.getField("HTTP_1_1").get(null));
                    builder.getMethod("code", int.class).invoke(response, lookup ? lookupStatus : 200);
                    builder.getMethod("message", String.class).invoke(response, "fixture");
                    builder.getMethod("body", body).invoke(response, body.getMethod("create", String.class, media)
                            .invoke(null, payload, media.getMethod("get", String.class).invoke(null, "application/json")));
                    return builder.getMethod("build").invoke(response);
                });
    }

    private Route nativeInfinityRoute() throws Exception {
        // Stock Infinity+ 8.4.0 obfuscation; drive the actual patched chain without using a Reddit account.
        installNativeTransport();
        Class<?> builder = type("lj5");
        Class<?> chain = type("dk5");
        Class<?> request = type("p7");
        Class<?> interceptor = type("c93");
        var requestField = chain.getDeclaredField("e");
        requestField.setAccessible(true);
        var urlField = request.getDeclaredField("r");
        urlField.setAccessible(true);
        Object stop = Proxy.newProxyInstance(context.getClassLoader(), new Class<?>[]{interceptor},
                (proxy, method, args) -> {
                    throw new Handoff(urlField.get(requestField.get(args[0])).toString());
                });
        var chainConstructor = chain.getDeclaredConstructors()[0];
        chainConstructor.setAccessible(true);
        Object pending = builder.getConstructor().newInstance();
        builder.getMethod("s", String.class).invoke(pending, "https://www.reddit.com/r/random/.json");
        Object unresolved = builder.getMethod("c").invoke(pending);
        // A non-null Exchange means the chain is about to send, not still authenticating.
        var exchangeConstructor = type("kl").getDeclaredConstructor(byte.class);
        exchangeConstructor.setAccessible(true);
        Object networkChain = chainConstructor.newInstance(null, new ArrayList<>(List.of(stop)),
                0, exchangeConstructor.newInstance((byte) 0), unresolved, 1000, 1000, 1000);
        try {
            chain.getMethod("b", request).invoke(networkChain, unresolved);
            throw new AssertionError("Infinity did not reject an unresolved Random URL at the network boundary");
        } catch (InvocationTargetException failure) {
            check(failure.getCause() instanceof IOException error && error.getMessage().contains("No Reddit session"),
                    "Expected the guard to abort before using the exchange: " + failure.getCause());
        }
        return url -> {
            Object input = builder.getConstructor().newInstance();
            builder.getMethod("s", String.class).invoke(input, url);
            builder.getMethod("m", String.class, String.class).invoke(input, "Authorization", TOKEN);
            Object requestObject = builder.getMethod("c").invoke(input);
            Object realChain = chainConstructor.newInstance(null, new ArrayList<>(List.of(stop)),
                    0, null, requestObject, 1000, 1000, 1000);
            try {
                chain.getMethod("b", request).invoke(realChain, requestObject);
                throw new AssertionError("Native chain did not reach the fixture interceptor");
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof Handoff handoff) return handoff.getMessage();
                throw failure;
            }
        };
    }

    private void installNativeTransport() {
        URL.setURLStreamHandlerFactory(protocol -> !"https".equals(protocol) ? null : new URLStreamHandler() {
            @Override protected HttpsURLConnection openConnection(URL url) {
                return new HttpsURLConnection(url) {
                    @Override public int getResponseCode() { return lookupStatus; }
                    @Override public ByteArrayInputStream getInputStream() throws IOException {
                        check(!getInstanceFollowRedirects(), "Fixture lookup enabled redirects");
                        return new ByteArrayInputStream(reply(URI.create(url.toString()),
                                getRequestProperty("Authorization")).getBytes(StandardCharsets.UTF_8));
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

    private String reply(URI url, String token) throws IOException {
        if ("api.redditrand.com".equals(url.getHost())) {
            check(token == null && "nsfw=false".equals(url.getQuery()), "RedditRand got auth or a mature request");
            providers.add("rand");
            return "{\"url\":\"/r/FixtureRand/\"}";
        }
        check("oauth.reddit.com".equals(url.getHost()) && TOKEN.equals(token), "Wrong Reddit lookup credentials");
        String community = "{\"display_name\":\"%s\",\"subreddit_type\":\"public\",\"over18\":false,\"subscribers\":500}";
        if ("/subreddits/search.json".equals(url.getPath())) {
            check(url.getQuery().contains("include_over_18=false"), "Search requested mature results");
            providers.add("search");
            return "{\"data\":{\"children\":[{\"kind\":\"t5\",\"data\":"
                    + community.formatted("FixtureSearch") + "}]}}";
        }
        if ("/r/FixtureRand/about.json".equals(url.getPath())) {
            providers.add("about");
            return "{\"data\":" + community.formatted("FixtureRand") + "}";
        }
        throw new IOException("Unexpected fixture URL " + url.getPath());
    }

    private Class<?> type(String name) throws Exception {
        return context.getClassLoader().loadClass(name);
    }

    private interface Route {
        String send(String url) throws Exception;
    }

    private static final class Handoff extends RuntimeException {
        Handoff(String url) { super(url); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
