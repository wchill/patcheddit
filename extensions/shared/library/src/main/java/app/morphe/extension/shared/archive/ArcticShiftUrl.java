/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.archive;

import java.net.URI;
import java.net.URISyntaxException;

public final class ArcticShiftUrl {
    public static final String DEFAULT_BASE_URL = "https://arctic-shift.photon-reddit.com/api/";

    private ArcticShiftUrl() {}

    public static String normalize(String value) {
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
}
