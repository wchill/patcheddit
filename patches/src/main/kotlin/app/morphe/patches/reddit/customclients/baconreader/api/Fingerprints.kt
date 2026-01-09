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

internal val getRestClientUserAgentFingerprint = Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/onelouder/baconreader/connectivity/RestClient;" && method.name == "getUserAgent"
    }
)

internal val getRedditUserAgentFingerprint = Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("RedditRetrofitClientModule;") && method.name == "getUserAgent"
    }
)

internal val getAuthorizeUrlFingerprint = Fingerprint(
    strings = listOf("redirect_uri=http://baconreader.com/auth")
)

internal val isRedirectUrlFingerprint = Fingerprint(
    strings = listOf("http://baconreader.com/auth"),
    custom = { method, _ ->
        method.name == "isRedirectUrl"
    }
)

internal val runTaskFingerprint = Fingerprint(
    strings = listOf("http://baconreader.com/auth"),
    custom = { method, _ ->
        method.name == "runTask"
    }
)
