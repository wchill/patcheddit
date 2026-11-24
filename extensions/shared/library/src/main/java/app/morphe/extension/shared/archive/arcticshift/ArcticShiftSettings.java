/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.archive.arcticshift;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.archive.ArcticShiftUrl;
import app.morphe.extension.shared.settings.PatchSettings;
import okhttp3.HttpUrl;

public final class ArcticShiftSettings {
    // Preserve the key used by existing Boost installations.
    public static final String BASE_URL = "morphe_boost_arctic_shift_base_url";

    private ArcticShiftSettings() {}

    public static String getBaseUrl() {
        return ArcticShiftUrl.normalize(Utils.isContextSet()
                ? PatchSettings.preferences(Utils.getContext()).getString(BASE_URL, "")
                : "");
    }

    static boolean isApiUrl(HttpUrl request, String baseUrl) {
        HttpUrl base = HttpUrl.get(baseUrl);
        return request.scheme().equals(base.scheme()) && request.host().equals(base.host())
                && request.port() == base.port()
                && request.encodedPath().startsWith(base.encodedPath());
    }
}
