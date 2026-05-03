/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.baconreader.api

import app.morphe.patcher.Fingerprint

internal object GetAuthorizationUrlFingerprint : Fingerprint(
    strings = listOf("client_id=zACVn0dSFGdWqQ")
)

internal object RequestTokenFingerprint : Fingerprint(
    strings = listOf(
        // App ID and secret.
        "zACVn0dSFGdWqQ",
        "kDm2tYpu9DqyWFFyPlNcXGEni4k"
    )
)

internal object GetRestClientUserAgentFingerprint : Fingerprint(
    definingClass = "Lcom/onelouder/baconreader/connectivity/RestClient;",
    name = "getUserAgent",
)

internal object GetRedditUserAgentFingerprint : Fingerprint(
    definingClass = "RedditRetrofitClientModule;",
    name = "getUserAgent",
)

internal object GetAuthorizeUrlFingerprint : Fingerprint(
    strings = listOf("redirect_uri=http://baconreader.com/auth")
)

internal object AuthUrlFingerprint : Fingerprint(
    strings = listOf("http://baconreader.com/auth"),
)
