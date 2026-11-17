/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.http.arcticshift;

import java.net.URI;
import java.net.URISyntaxException;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.PatchSettings;
import okhttp3.HttpUrl;

public final class ArcticShiftSettings {
    public static final String BASE_URL = "morphe_boost_arctic_shift_base_url";
    public static final String DEFAULT_BASE_URL = "https://arctic-shift.photon-reddit.com/api/";

    private ArcticShiftSettings() {}

    public static String getBaseUrl() {
        return normalizeBaseUrl(Utils.isContextSet()
                ? PatchSettings.preferences(Utils.getContext()).getString(BASE_URL, "")
                : "");
    }

    public static String normalizeBaseUrl(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return DEFAULT_BASE_URL;
        try {
            URI uri = new URI(text);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("Enter an HTTP(S) API base URL without credentials, a query or a fragment.");
            }
            String url = uri.toASCIIString();
            return url.endsWith("/") ? url : url + "/";
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("Enter a valid HTTP(S) API base URL.", invalid);
        }
    }

    static boolean isApiUrl(HttpUrl request, String baseUrl) {
        HttpUrl base = HttpUrl.get(baseUrl);
        return request.scheme().equals(base.scheme()) && request.host().equals(base.host())
                && request.port() == base.port()
                && request.encodedPath().startsWith(base.encodedPath());
    }
}
