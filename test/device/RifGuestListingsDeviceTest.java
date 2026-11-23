/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;

public final class RifGuestListingsDeviceTest {
    private static final String HTTP = "app.morphe.extension.shared.http.libraries.okhttp3.";
    private static final String WEB = "https://www.reddit.com";
    private static final String OAUTH = "https://oauth.reddit.com";
    private static final String TOKEN = "fixture-guest-token";

    public static void run(Context context, Bundle arguments) throws Exception {
        ClassLoader loader = context.getClassLoader();
        boolean paid = context.getPackageName().endsWith("donation");
        String prefix = paid ? "i3." : "h3.";
        Class<?> prefs = loader.loadClass(paid ? "l4.v" : "k4.v");
        Object settings = prefs.getMethod("C").invoke(null);
        Field selected = field(prefs, "Z0"), token = field(prefs, "e1"), expiry = field(prefs, "f1");
        Object oldAccount = selected.get(settings), oldToken = token.get(settings);
        long oldExpiry = expiry.getLong(settings);
        Class<?> clientClass = loader.loadClass(prefix + "d");
        Object client = clientClass.getMethod("f").invoke(null);
        Field clientField = field(clientClass, "a");
        boolean live = "true".equals(arguments.getString("live"));
        List<String> hosts = new ArrayList<>();
        List<Integer> statuses = new ArrayList<>();
        Object injected = spy(loader, client, hosts, statuses, live);
        Class<?> base = loader.loadClass(prefix + "c"), loaderType = loader.loadClass(prefix + "a");
        Class<?> feedType = loader.loadClass(paid ? "c5.y0" : "b5.y0");
        Constructor<?> worker = loader.loadClass(prefix + "a$a").getDeclaredConstructor(
                Uri.class, Context.class, boolean.class, loaderType);
        worker.setAccessible(true);
        Method execute = method(base, "G"), useGuest = method(base, "S");
        Field retry = field(base, "p");
        try {
            selected.set(settings, null);
            if (!live) {
                token.set(settings, TOKEN);
                expiry.setLong(settings, Long.MAX_VALUE);
            }
            clientField.set(null, injected);
            FutureTask<Activity> create = new FutureTask<>(() -> new FixtureActivity(context));
            PatchedditInstrumentation.instance.runOnMainSync(create);
            Activity activity = create.get();
            for (String route : List.of("/.json", "/r/Android/new/.json?limit=2", "/comments/1skmtzo/.json")) {
                Uri uri = Uri.parse(WEB + route);
                Object feed = feedType.getConstructor(Activity.class, Uri.class).newInstance(activity, uri);
                Object request = worker.newInstance(uri, context, false, feed);
                check(Boolean.TRUE.equals(useGuest.invoke(request)), "Guest OAuth was not selected for " + route);
                if (!live || !route.startsWith("/comments/")) execute.invoke(request);
                retry.setBoolean(request, true);
                check(Boolean.FALSE.equals(useGuest.invoke(request)), "Account retry switched to guest OAuth");
            }
            check(hosts.equals(live ? List.of(OAUTH, OAUTH) : List.of(OAUTH, OAUTH, OAUTH)),
                    "Native worker sent the listing to the legacy host");
            check(statuses.stream().allMatch(status -> status == 200), "Guest listing failed: " + statuses);
            if (live) System.out.println("RIF guest listing HTTP statuses: " + statuses);

            for (String url : List.of(WEB + "/api/info.json", WEB + "/message/inbox/.json",
                    WEB + "/r/Android/about/rules.json", "https://example.com/.json",
                    "https://www.reddit.com@example.com/.json", "https://oauth.reddit.com:443/.json")) {
                Object request = worker.newInstance(Uri.parse(url), context, false, null);
                check(Boolean.FALSE.equals(useGuest.invoke(request)), "Non-listing URL used guest OAuth: " + url);
            }
            Constructor<?> postWorker = loader.loadClass(prefix + "a$b").getDeclaredConstructor(
                    Uri.class, Context.class, boolean.class, loaderType);
            postWorker.setAccessible(true);
            Object post = postWorker.newInstance(Uri.parse(WEB + "/.json"), context, false, null);
            check(Boolean.FALSE.equals(useGuest.invoke(post)), "POST used guest OAuth");
            selected.set(settings, "patcheddit-fixture-account");
            Object request = worker.newInstance(Uri.parse(WEB + "/.json"), context, false, null);
            check(method(base, "I").invoke(request) != null, "Could not simulate a selected account");
            check(Boolean.FALSE.equals(useGuest.invoke(request)), "Selected account switched to guest OAuth");
            check(Boolean.TRUE.equals(useGuest.invoke(worker.newInstance(
                    Uri.parse(OAUTH + "/api/v1/me"), context, false, null))), "Stock guest OAuth was disabled");
        } finally {
            clientField.set(null, client);
            expiry.setLong(settings, oldExpiry);
            token.set(settings, oldToken);
            selected.set(settings, oldAccount);
        }
    }

    private static Object spy(ClassLoader loader, Object client, List<String> hosts,
                              List<Integer> statuses, boolean live) throws Exception {
        Object builder = client.getClass().getMethod("newBuilder").invoke(client);
        Class<?> interceptor = loader.loadClass(HTTP + "Interceptor");
        Class<?> chainType = loader.loadClass(HTTP + "Interceptor$Chain");
        Class<?> requestType = loader.loadClass(HTTP + "Request");
        Class<?> bodyType = loader.loadClass(HTTP + "ResponseBody");
        Class<?> responseType = loader.loadClass(HTTP + "Response");
        Class<?> protocol = loader.loadClass(HTTP + "Protocol");
        Class<?> responseBuilder = loader.loadClass(HTTP + "Response$Builder");
        Object transport = Proxy.newProxyInstance(loader, new Class<?>[]{interceptor}, (proxy, method, args) -> {
            if (method.getName().equals("toString")) return "RIF guest listing fixture";
            check(method.getName().equals("intercept"), "Unexpected interceptor method");
            Object chain = args[0], request = chainType.getMethod("request").invoke(chain);
            java.net.URI url = java.net.URI.create(requestType.getMethod("url").invoke(request).toString());
            if (live && (WEB + "/api/v1/access_token").equals(url.toString())) {
                return chainType.getMethod("proceed", requestType).invoke(chain, request);
            }
            String auth = (String) requestType.getMethod("header", String.class).invoke(request, "Authorization");
            check("GET".equals(requestType.getMethod("method").invoke(request)), "Listing was no longer a GET");
            check(live ? auth != null && auth.startsWith("Bearer ") : ("Bearer " + TOKEN).equals(auth),
                    "Native listing did not carry its guest bearer at " + url.getHost() + url.getPath()
                            + " (authorization present: " + (auth != null) + ")");
            hosts.add(url.getScheme() + "://" + url.getAuthority());
            if (live) {
                Object response = chainType.getMethod("proceed", requestType).invoke(chain, request);
                statuses.add((Integer) responseType.getMethod("code").invoke(response));
                return response;
            }
            Object response = responseBuilder.getConstructor().newInstance();
            responseBuilder.getMethod("request", requestType).invoke(response, request);
            responseBuilder.getMethod("protocol", protocol).invoke(response, protocol.getField("HTTP_1_1").get(null));
            responseBuilder.getMethod("code", int.class).invoke(response, 200);
            responseBuilder.getMethod("message", String.class).invoke(response, "OK");
            Object body = bodyType.getMethod("create", loader.loadClass(HTTP + "MediaType"), String.class)
                    .invoke(null, null, "{\"kind\":\"Listing\",\"data\":{\"children\":[]}}");
            responseBuilder.getMethod("body", bodyType).invoke(response, body);
            statuses.add(200);
            return responseBuilder.getMethod("build").invoke(response);
        });
        builder.getClass().getMethod("cache", loader.loadClass(HTTP + "Cache")).invoke(builder, new Object[]{null});
        builder.getClass().getMethod("addInterceptor", interceptor).invoke(builder, transport);
        return builder.getClass().getMethod("build").invoke(builder);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name) throws Exception {
        Method method = type.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    private static void check(boolean expected, String message) {
        if (!expected) throw new AssertionError(message);
    }

    private static final class FixtureActivity extends Activity {
        FixtureActivity(Context context) { attachBaseContext(context); }
    }
}
