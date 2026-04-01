/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.baconreader.api

import app.morphe.patcher.Fingerprint

internal val getAuthorizationUrlFingerprint = Fingerprint(
    strings = listOf("client_id=zACVn0dSFGdWqQ")
)

internal val getClientIdFingerprint = Fingerprint(
    definingClass = "RedditOAuth;",
    name = "getAuthorizeUrl",
    strings = listOf("client_id=zACVn0dSFGdWqQ"),
)

internal val requestTokenFingerprint = Fingerprint(
    strings = listOf(
        // App ID and secret.
        "zACVn0dSFGdWqQ",
        "kDm2tYpu9DqyWFFyPlNcXGEni4k"
    )
)

internal val getRestClientUserAgentFingerprint = Fingerprint(
    definingClass = "Lcom/onelouder/baconreader/connectivity/RestClient;",
    name = "getUserAgent",
)

internal val getRedditUserAgentFingerprint = Fingerprint(
    definingClass = "RedditRetrofitClientModule;",
    name = "getUserAgent",
)

internal val getAuthorizeUrlFingerprint = Fingerprint(
    strings = listOf("redirect_uri=http://baconreader.com/auth")
)

internal val authUrlFingerprint = Fingerprint(
    strings = listOf("http://baconreader.com/auth"),
)
