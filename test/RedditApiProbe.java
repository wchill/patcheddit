/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

import dalvik.system.PathClassLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public final class RedditApiProbe {
    private static ClassLoader loader;
    private static String library;
    private static final AtomicInteger requests = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        loader = new PathClassLoader(args[0], RedditApiProbe.class.getClassLoader());
        library = args[1].equals("boost") ? ""
                : "app.morphe.extension.shared.http.libraries.";
        Class<?> patchType = Class.forName(
                "app.morphe.extension.shared.fixes.api.RedditInfoResponsePatch", true, loader);
        Object patch = patchType.getConstructor().newInstance();
        check((Boolean) patchType.getMethod("isPatchIncluded").invoke(patch), "lookup patch is disabled");

        Result thread = execute(patch, "/comments/post.json");
        check(new JSONArray(thread.body).length() == 2, "context observer changed thread response");
        patch = patchType.getConstructor().newInstance();
        Result comment = execute(patch, "/api/info.json?id=t1_child");
        JSONObject data = children(comment).getJSONObject(0).getJSONObject("data");
        check(data.getString("name").equals("t1_child"), "comment lookup lost its fullname");
        check(data.getString("parent_id").equals("t3_post"), "comment lookup changed its parent");
        check(data.getString("replies").isEmpty(), "comment lookup included a nested reply tree");

        Result posts = execute(patch, "/api/info?id=t3_post,t3_other,t3_post&raw_json=1");
        check(children(posts).length() == 2, "post batch order or deduplication changed");
        check(children(posts).getJSONObject(1).getJSONObject("data").getString("name")
                .equals("t3_other"), "post batch returned the wrong post");
        Result byId = execute(patch, "/r/test/by_id/t3_post%2Ct3_other.json");
        check(children(byId).length() == 2, "by_id alias did not return both posts");
        Result communities = execute(patch, "/r/test/api/info/.json?sr_name=java,learnjava");
        check(children(communities).getJSONObject(0).getString("kind").equals("t5"),
                "community lookup lost its thing kind");
        check(children(communities).getJSONObject(1).getJSONObject("data")
                .getString("display_name").equals("learnjava"), "community lookup lost its order");

        Result denied = execute(patch, "/api/info?id=t3_private");
        check(denied.code == 403 && new JSONObject(denied.body).getString("reason").equals("private"),
                "lookup masked a real permission error");
        Result unknown = execute(patch, "/api/info?id=t1_unknown");
        check(unknown.code == 403 && unknown.body.equals("blocked"),
                "unknown comment context became a false successful lookup");
        check(requests.get() == 14, "unexpected fallback request count: " + requests.get());
        System.out.println("PASS " + args[1]
                + ": enabled lookup marker, shared comment context, post/by_id/community listings,"
                + " scoped routes, credentials, permission errors, missing context; no network used");
    }

    private static JSONArray children(Result result) throws Exception {
        check(result.code == 200, "lookup failed with HTTP " + result.code);
        return new JSONObject(result.body).getJSONObject("data").getJSONArray("children");
    }

    private static Result execute(Object patch, String path) throws Exception {
        Class<?> requestBuilderType = type("okhttp3.Request$Builder");
        Object builder = requestBuilderType.getConstructor().newInstance();
        requestBuilderType.getMethod("url", String.class).invoke(builder, "https://oauth.reddit.com" + path);
        requestBuilderType.getMethod("header", String.class, String.class)
                .invoke(builder, "Authorization", "fixture-authorization");
        requestBuilderType.getMethod("header", String.class, String.class)
                .invoke(builder, "Cookie", "fixture-cookie");
        if (path.contains("/api/info") || path.contains("/by_id/")) {
            requestBuilderType.getMethod("header", String.class, String.class)
                    .invoke(builder, "If-None-Match", "original-info-only");
            requestBuilderType.getMethod("header", String.class, String.class)
                    .invoke(builder, "Range", "bytes=0-100");
        }
        Object request = requestBuilderType.getMethod("build").invoke(builder);
        Class<?> chainType = type("okhttp3.Interceptor$Chain");
        Object chain = Proxy.newProxyInstance(loader, new Class<?>[]{chainType},
                (proxy, method, parameters) -> {
                    if (method.getName().equals("request")) return request;
                    if (method.getName().equals("proceed")) {
                        requests.incrementAndGet();
                        return response(parameters[0]);
                    }
                    if (method.getName().equals("toString")) return "fixture chain";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == parameters[0];
                    throw new AssertionError(method);
                });
        Object response = type("okhttp3.Interceptor").getMethod("intercept", chainType).invoke(patch, chain);
        Class<?> responseType = type("okhttp3.Response");
        try {
            check(responseType.getMethod("request").invoke(response) == request,
                    "response no longer belongs to the original request");
            int code = (Integer) responseType.getMethod("code").invoke(response);
            Object body = responseType.getMethod("body").invoke(response);
            return new Result(code, (String) type("okhttp3.ResponseBody").getMethod("string").invoke(body));
        } finally {
            responseType.getMethod("close").invoke(response);
        }
    }

    private static Object response(Object request) throws Exception {
        Class<?> requestType = type("okhttp3.Request");
        check(requestType.getMethod("header", String.class).invoke(request, "Authorization")
                .equals("fixture-authorization"), "lookup changed authorization");
        check(requestType.getMethod("header", String.class).invoke(request, "Cookie")
                .equals("fixture-cookie"), "lookup changed cookies");
        Object url = requestType.getMethod("url").invoke(request);
        String path = (String) type("okhttp3.HttpUrl").getMethod("encodedPath").invoke(url);
        String id = (String) type("okhttp3.HttpUrl").getMethod("queryParameter", String.class).invoke(url, "id");
        int code = 200;
        String mime = "application/json";
        String content;
        if (path.contains("/api/info") || path.contains("/by_id/")) {
            code = 403;
            if ("t3_private".equals(id)) {
                content = "{\"error\":403,\"reason\":\"private\"}";
            } else {
                mime = "text/html";
                content = "blocked";
            }
        } else if (path.startsWith("/r/")) {
            check(requestType.getMethod("header", String.class).invoke(request, "If-None-Match") == null,
                    "community fallback kept a validator for another resource");
            check(requestType.getMethod("header", String.class).invoke(request, "Range") == null,
                    "community fallback kept an unrelated range");
            String name = path.split("/")[2];
            content = "{\"kind\":\"t5\",\"data\":{\"id\":\"community\",\"name\":\"t5_community\","
                    + "\"display_name\":\"" + name + "\"}}";
        } else {
            check(requestType.getMethod("header", String.class).invoke(request, "If-None-Match") == null,
                    "focused fallback kept a validator for another resource");
            check(requestType.getMethod("header", String.class).invoke(request, "Range") == null,
                    "focused fallback kept an unrelated range");
            String post = path.startsWith("/comments/other") ? "other" : "post";
            content = "[{\"kind\":\"Listing\",\"data\":{\"children\":[{\"kind\":\"t3\","
                    + "\"data\":{\"id\":\"" + post + "\",\"name\":\"t3_" + post + "\"}}]}},"
                    + "{\"kind\":\"Listing\",\"data\":{\"children\":[{\"kind\":\"t1\","
                    + "\"data\":{\"id\":\"child\",\"name\":\"t1_child\",\"link_id\":\"t3_post\","
                    + "\"parent_id\":\"t3_post\",\"replies\":\"\"}}]}}]";
        }
        Class<?> mediaType = type("okhttp3.MediaType");
        Object media = mediaType.getMethod("get", String.class).invoke(null, mime);
        Object body = type("okhttp3.ResponseBody").getMethod("create", String.class, mediaType)
                .invoke(null, content, media);
        Class<?> protocolType = type("okhttp3.Protocol");
        Object protocol = protocolType.getField("HTTP_1_1").get(null);
        Class<?> builderType = type("okhttp3.Response$Builder");
        Object builder = builderType.getConstructor().newInstance();
        builderType.getMethod("request", requestType).invoke(builder, request);
        builderType.getMethod("protocol", protocolType).invoke(builder, protocol);
        builderType.getMethod("code", int.class).invoke(builder, code);
        builderType.getMethod("message", String.class).invoke(builder, "fixture");
        builderType.getMethod("header", String.class, String.class).invoke(builder, "Content-Type", mime);
        builderType.getMethod("body", type("okhttp3.ResponseBody")).invoke(builder, body);
        return builderType.getMethod("build").invoke(builder);
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(library + name, true, loader);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Result {
        final int code;
        final String body;

        Result(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }
}
