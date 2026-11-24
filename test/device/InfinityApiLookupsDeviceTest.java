/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class InfinityApiLookupsDeviceTest {
    private static final String BASE = "https://oauth.reddit.com";
    private static final String AUTH = "Bearer fixture-access-token";
    private static final String POST = "{\"kind\":\"t3\",\"data\":{\"id\":\"example\",\"name\":\"t3_example\"}}";
    private static final String COMMENT = "{\"kind\":\"t1\",\"data\":{\"id\":\"child\",\"name\":\"t1_child\","
            + "\"link_id\":\"t3_example\",\"parent_id\":\"t3_example\",\"depth\":0,\"replies\":\"\"}}";
    private static final String THREAD = "[" + listing(POST) + "," + listing(COMMENT) + "]";
    private static final String FAILED_GALLERY = "["
            + listing("{\"kind\":\"t3\",\"data\":{\"id\":\"galleryfixture\",\"name\":\"t3_galleryfixture\","
                + "\"gallery_data\":{\"items\":[{\"media_id\":\"morphefixture\"}]},"
                + "\"media_metadata\":{\"morphefixture\":{\"status\":\"failed\",\"m\":\"image/png\"}}}}")
            + "," + listing("") + "]";

    public static void run(Context context, Bundle args) throws Exception {
        check(context.getPackageName().startsWith("app.patcheddit.rollout."), "Requires a disposable Infinity APK");
        new Fixture(context.getClassLoader(), args.getString("stock", "plus")).verify();
    }

    // Stock names are fixture data; the production patch resolves the corresponding shapes.
    private static final class Fixture {
        final ClassLoader loader;
        final boolean legacy;
        final Class<?> clientType, callType, builderType, formType, requestType, responseType, bufferType, bodyType;
        final String execute, responseBody, responseCode, bodyString, requestMethod, requestHeaders, requestUrl, setHeader;
        final Method parseMediaType;
        final Object mediaTypeCompanion;
        final List<String> routes = new ArrayList<>();
        final Object client;
        boolean blockChildren = true, challenge;

        Fixture(ClassLoader loader, String stock) throws Exception {
            this.loader = loader;
            legacy = stock.equals("legacy-plus");
            boolean patreon = stock.equals("patreon");
            clientType = type(legacy ? "cc.z" : patreon ? "df4" : "lj4");
            callType = type(legacy ? "hc.e" : patreon ? "pf5" : "zj5");
            builderType = type(legacy ? "cc.B$a" : patreon ? "bf5" : "lj5");
            formType = type(legacy ? "cc.s" : patreon ? "lf2" : "ki2");
            requestType = type(legacy ? "cc.B" : patreon ? "n7" : "p7");
            responseType = type(legacy ? "cc.D" : patreon ? "vn5" : "ds5");
            bufferType = type(legacy ? "pc.d" : patreon ? "da0" : "ac0");
            bodyType = type(legacy ? "cc.E$a$a" : patreon ? "uf5" : "ek5");
            execute = legacy ? "c" : patreon ? "d" : "e";
            responseBody = legacy ? "u" : patreon ? "w" : "x";
            responseCode = legacy ? "r" : patreon ? "t" : "u";
            bodyString = legacy ? "G" : "l";
            requestMethod = legacy ? "b" : patreon ? "v" : "w";
            requestHeaders = legacy ? "c" : patreon ? "s" : "t";
            requestUrl = legacy ? "a" : "r";
            setHeader = legacy ? "e" : patreon ? "l" : "m";
            parseMediaType = type(legacy ? "cc.x$a" : patreon ? "bb0" : "oq2")
                    .getMethod(legacy ? "a" : patreon ? "s" : "L", String.class);
            mediaTypeCompanion = legacy ? type("cc.x").getField("e").get(null) : null;
            Class<?> interceptor = type(legacy ? "cc.w" : patreon ? "r53" : "c93");
            Object transport = Proxy.newProxyInstance(loader, new Class<?>[]{interceptor}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return "Infinity API fixture";
                Object request = legacy ? args[0].getClass().getMethod("d").invoke(args[0])
                        : args[0].getClass().getField("e").get(args[0]);
                return reply(request);
            });
            client = clientType.getConstructor().newInstance();
            var interceptors = clientType.getDeclaredField(legacy ? "q" : patreon ? "s" : "t");
            interceptors.setAccessible(true);
            interceptors.set(client, List.of(transport));
        }

        void verify() throws Exception {
            String byId = "/by_id/t3_example.json";
            String focus = "/comments/example.json";
            check(request(byId, null).code == 200, "Blocked by_id didn't recover");
            check(routes.equals(List.of("GET " + byId, "GET " + focus)), "Wrong by_id request sequence: " + routes);
            routes.clear();
            Reply post = request("/api/info.json?raw_json=1", form(List.of("id"), List.of("t3_example")));
            check(post.code == 200 && children(post).getJSONObject(0).getJSONObject("data")
                    .getString("name").equals("t3_example"), "Native Retrofit form didn't recover the post");
            check(routes.equals(List.of("POST /api/info.json", "GET " + focus)), "Wrong info request sequence: " + routes);

            check(request(focus, null).text.equals(THREAD), "Context indexing consumed a native response body");
            routes.clear();
            Reply comment = request("/api/info.json?id=t1_child", null);
            check(comment.code == 200 && children(comment).getJSONObject(0).getJSONObject("data")
                    .getString("name").equals("t1_child"), "Comment lookup didn't use observed thread context");
            check(routes.equals(List.of("GET /api/info.json", "GET /comments/example/comment/child.json")),
                    "Wrong comment lookup sequence: " + routes);

            String more = "/api/morechildren.json";
            routes.clear();
            Reply children = request(more, form(List.of("link_id", "children"), List.of("t3_example", "child")));
            check(children.code == 200 && new JSONObject(children.text).getJSONObject("json").getJSONObject("data")
                    .getJSONArray("things").getJSONObject(0).getJSONObject("data").getString("name").equals("t1_child"),
                    "Morechildren fallback didn't produce Infinity's native response shape");
            check(routes.equals(List.of("POST " + more, "GET /comments/example/comment/child.json")),
                    "Wrong morechildren sequence: " + routes);

            routes.clear();
            blockChildren = false;
            check(request(more + "?link_id=t3_example&children=child", null).text.equals("{\"json\":{\"native\":true}}")
                    && routes.equals(List.of("GET " + more)), "Working morechildren request was replaced");
            challenge = true;
            routes.clear();
            check(request(byId, null).code == 403 && routes.equals(List.of("GET " + byId)),
                    "An actual OAuth challenge must not be retried");

            var cdnRequests = new ArrayList<String>();
            interceptGalleryCdn(cdnRequests);
            Reply gallery = request("/comments/galleryfixture.json", null);
            JSONObject galleryPost = new org.json.JSONArray(gallery.text).getJSONObject(0).getJSONObject("data")
                    .getJSONArray("children").getJSONObject(0).getJSONObject("data");
            JSONObject source = galleryPost.getJSONObject("media_metadata").getJSONObject("morphefixture").getJSONObject("s");
            check(gallery.code == 200 && source.getInt("x") == 80 && source.getInt("y") == 40
                            && source.getString("u").equals("https://i.redd.it/morphefixture.png"),
                    "Infinity did not receive displayable gallery metadata");
            check(cdnRequests.equals(List.of("https://i.redd.it/morphefixture.png")),
                    "The native gallery response made unexpected CDN requests");
        }

        private Class<?> type(String name) throws ClassNotFoundException { return loader.loadClass(name); }

        private void interceptGalleryCdn(List<String> requests) throws Exception {
            String http = "app.morphe.extension.shared.http.libraries.okhttp3.";
            Class<?> interceptor = type(http + "Interceptor");
            Class<?> chain = type(http + "Interceptor$Chain");
            Class<?> requestType = type(http + "Request");
            Class<?> mediaType = type(http + "MediaType");
            Class<?> bodyType = type(http + "ResponseBody");
            Class<?> protocol = type(http + "Protocol");
            Class<?> builderType = type(http + "Response$Builder");
            byte[] png;
            Bitmap bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888);
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes), "Could not make fixture image");
                png = bytes.toByteArray();
            } finally {
                bitmap.recycle();
            }
            Object fake = Proxy.newProxyInstance(loader, new Class<?>[]{interceptor}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return "Infinity gallery fixture";
                Object request = chain.getMethod("request").invoke(args[0]);
                check("GET".equals(requestType.getMethod("method").invoke(request)), "Missing gallery range GET");
                Method header = requestType.getMethod("header", String.class);
                check("bytes=0-65535".equals(header.invoke(request, "Range"))
                                && header.invoke(request, "Authorization") == null && header.invoke(request, "Cookie") == null,
                        "Unbounded or credentialed gallery request");
                requests.add(requestType.getMethod("url").invoke(request).toString());
                Object builder = builderType.getConstructor().newInstance();
                builderType.getMethod("request", requestType).invoke(builder, request);
                builderType.getMethod("code", int.class).invoke(builder, 206);
                builderType.getMethod("message", String.class).invoke(builder, "Partial Content");
                builderType.getMethod("protocol", protocol).invoke(builder, protocol.getField("HTTP_1_1").get(null));
                builderType.getMethod("body", bodyType).invoke(builder, bodyType.getMethod("create", byte[].class, mediaType)
                        .invoke(null, png, mediaType.getMethod("get", String.class).invoke(null, "image/png")));
                return builderType.getMethod("build").invoke(builder);
            });
            var instance = type("app.morphe.extension.shared.fixes.api.FailedGalleryMetadataPatch$LegacyClient")
                    .getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            Object cdn = instance.get(null);
            var interceptors = cdn.getClass().getDeclaredField("interceptors");
            interceptors.setAccessible(true);
            interceptors.set(cdn, List.of(fake));
        }

        private Object form(List<String> names, List<String> values) throws Exception {
            Class<?> lists = legacy ? List.class : ArrayList.class;
            return formType.getConstructor(lists, lists)
                    .newInstance(new ArrayList<>(names), new ArrayList<>(values));
        }

        private Reply request(String path, Object form) throws Exception {
            Object builder = builderType.getConstructor().newInstance();
            builderType.getMethod(legacy ? "k" : "s", String.class).invoke(builder, BASE + path);
            builderType.getMethod(setHeader, String.class, String.class).invoke(builder, "Authorization", AUTH);
            if (form != null) {
                builderType.getMethod(legacy ? "g" : "o", String.class, formType.getSuperclass())
                        .invoke(builder, "POST", form);
                builderType.getMethod(setHeader, String.class, String.class).invoke(builder, "Content-Type",
                        "application/x-www-form-urlencoded");
            }
            Object nativeRequest = builderType.getMethod(legacy ? "b" : "c").invoke(builder);
            Object call = legacy ? callType.getConstructor(clientType, requestType, boolean.class)
                    .newInstance(client, nativeRequest, false)
                    : callType.getConstructor(clientType, requestType).newInstance(client, nativeRequest);
            Object result = callType.getMethod(execute).invoke(call);
            Object body = responseType.getField(responseBody).get(result);
            return new Reply(responseType.getField(responseCode).getInt(result),
                    (String) bodyType.getSuperclass().getMethod(bodyString).invoke(body));
        }

        private Object reply(Object request) throws Exception {
            String url = requestType.getField(requestUrl).get(request).toString();
            String path = android.net.Uri.parse(url).getPath();
            String method = (String) requestType.getField(requestMethod).get(request);
            var constructor = Arrays.stream(responseType.getConstructors())
                    .filter(c -> c.getParameterCount() == 13).findFirst().orElseThrow();
            Class<?> headers = constructor.getParameterTypes()[5];
            Method header = Arrays.stream(headers.getMethods()).filter(m -> m.getReturnType() == String.class
                    && Arrays.equals(m.getParameterTypes(), new Class<?>[]{String.class})).findFirst().orElseThrow();
            Object headersIn = requestType.getField(requestHeaders).get(request);
            check(AUTH.equals(header.invoke(headersIn, "Authorization")), "Native retry dropped the bearer");
            if ("GET".equals(method)) check(header.invoke(headersIn, "Content-Type") == null,
                    "Retry leaked the POST body headers");
            routes.add(method + " " + path);
            boolean more = path.equals("/api/morechildren.json");
            boolean blocked = path.startsWith("/by_id/") || path.equals("/api/info.json") || (more && blockChildren);
            String text = blocked ? "network security block" : more ? "{\"json\":{\"native\":true}}"
                    : path.equals("/comments/galleryfixture.json") ? FAILED_GALLERY : THREAD;
            String mime = blocked ? "text/html" : "application/json";
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            Object buffer = bufferType.getConstructor().newInstance();
            bufferType.getMethod("write", byte[].class).invoke(buffer, bytes);
            Object type = parseMediaType.invoke(mediaTypeCompanion, mime);
            var bodyConstructor = bodyType.getConstructors()[0];
            Object body = legacy ? bodyConstructor.newInstance(type, (long) bytes.length, buffer)
                    : bodyConstructor.newInstance(type, (long) bytes.length, buffer, (byte) 1);
            Object protocol = Arrays.stream(constructor.getParameterTypes()[1].getEnumConstants())
                    .filter(p -> p.toString().equals("http/1.1")).findFirst().orElseThrow();
            String[] pairs = blocked && challenge
                    ? new String[]{"Content-Type", mime, "WWW-Authenticate", "Bearer error=\"invalid_token\""}
                    : new String[]{"Content-Type", mime};
            var headersConstructor = headers.getDeclaredConstructor(String[].class);
            headersConstructor.setAccessible(true);
            Object headersOut = headersConstructor.newInstance((Object) pairs);
            return constructor.newInstance(request, protocol, blocked ? "Forbidden" : "OK", blocked ? 403 : 200,
                    null, headersOut, body, null, null, null, 0L, 0L, null);
        }
    }

    private record Reply(int code, String text) {}

    private static org.json.JSONArray children(Reply reply) throws Exception {
        return new JSONObject(reply.text).getJSONObject("data").getJSONArray("children");
    }

    private static String listing(String items) {
        return "{\"kind\":\"Listing\",\"data\":{\"children\":[" + items + "],\"before\":null,\"after\":null}}";
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
