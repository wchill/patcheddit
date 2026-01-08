package app.morphe.patches.reddit.customclients.baconreader.api

import app.morphe.patcher.Fingerprint

internal val getAuthorizationUrlFingerprint = Fingerprint(
    strings = listOf("client_id=zACVn0dSFGdWqQ")
)

internal val getClientIdFingerprint = Fingerprint(
    strings = listOf("client_id=zACVn0dSFGdWqQ"),
    custom = {
            method, classDef ->
        if (!classDef.endsWith("RedditOAuth;")) return@Fingerprint false
        method.name == "getAuthorizeUrl"
    }
)

internal val requestTokenFingerprint = Fingerprint(
    strings = listOf(
        // App ID and secret.
        "zACVn0dSFGdWqQ",
        "kDm2tYpu9DqyWFFyPlNcXGEni4k"
    )
)
