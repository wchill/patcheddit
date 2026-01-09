package app.morphe.patches.reddit.customclients.sync.syncforreddit.api

import app.morphe.patcher.Fingerprint

internal val getAuthorizationStringFingerprint = Fingerprint(
    strings = listOf("authorize.compact?client_id")
)

internal val getBearerTokenFingerprint = Fingerprint(
    strings = listOf("Basic")
)

internal val getUserAgentFingerprint = Fingerprint(
    strings = listOf("android:com.laurencedawson.reddit_sync")
)

internal val imgurImageAPIFingerprint = Fingerprint(
    strings = listOf("https://imgur-apiv3.p.rapidapi.com/3/image")
)

internal val getRedirectUriFingerprint = Fingerprint(
    strings = listOf("http://redditsync/auth")
)
