/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.requests;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;

public final class RedditRequestParameters {
    private RedditRequestParameters() {}

    public static Map<String, String> read(Request request) throws IOException {
        Map<String, String> parameters = new HashMap<>();
        for (String name : request.url().queryParameterNames()) {
            String value = request.url().queryParameter(name);
            if (value != null) parameters.put(name, value);
        }

        RequestBody body = request.body();
        if (body instanceof FormBody) {
            FormBody form = (FormBody) body;
            for (int i = 0; i < form.size(); i++) {
                parameters.put(form.name(i), form.value(i));
            }
        } else if (body != null) {
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            String form = new String(buffer.readByteArray(), StandardCharsets.UTF_8);
            try {
                for (String field : form.split("&")) {
                    if (field.isEmpty()) continue;
                    int separator = field.indexOf('=');
                    String name = separator < 0 ? field : field.substring(0, separator);
                    String value = separator < 0 ? "" : field.substring(separator + 1);
                    parameters.put(URLDecoder.decode(name, "UTF-8"),
                            URLDecoder.decode(value, "UTF-8"));
                }
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid URL-encoded Reddit API form", exception);
            }
        }
        return parameters;
    }
}
