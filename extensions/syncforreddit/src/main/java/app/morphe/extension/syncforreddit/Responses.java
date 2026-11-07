/*
 * Copyright 2026 trulow.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 * Adapted from https://github.com/notBrian2/revanced-rif-fixes.
 */

package app.morphe.extension.syncforreddit;

import com.android.volley.NetworkResponse;

import java.nio.charset.Charset;

/** Helpers shared by the Sync fixes. */
final class Responses {
    static final String TAG = "Patcheddit";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private Responses() {
    }

    static String body(NetworkResponse response) {
        return new String(response.data, UTF_8);
    }

    /**
     * A copy of the response with a new body. Volley caches what the request parses, so the copy is
     * what lands in Sync's cache too.
     */
    static NetworkResponse withBody(NetworkResponse response, String body) {
        return new NetworkResponse(response.statusCode, body.getBytes(UTF_8), response.headers,
                response.notModified, response.networkTimeMs);
    }
}
