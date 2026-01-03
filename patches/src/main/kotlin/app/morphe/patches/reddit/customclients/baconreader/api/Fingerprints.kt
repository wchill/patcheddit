package app.morphe.patches.reddit.customclients.baconreader.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

internal val getAuthorizationUrlFingerprint = Fingerprint(
    filters = listOf(string("client_id=zACVn0dSFGdWqQ"))
)

internal val getClientIdFingerprint = Fingerprint(
    filters = listOf(string("client_id=zACVn0dSFGdWqQ")),
    custom = {
            method, classDef ->
        if (!classDef.endsWith("RedditOAuth;")) return@Fingerprint false
        method.name == "getAuthorizeUrl"
    }
)

internal val requestTokenFingerprint = Fingerprint(
    filters = listOf(
        // App ID and secret.
        string("zACVn0dSFGdWqQ"),
        string("kDm2tYpu9DqyWFFyPlNcXGEni4k")
    )
)
