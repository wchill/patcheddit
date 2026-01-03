package app.morphe.patches.reddit.customclients.sync.syncforreddit.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

internal val getAuthorizationStringFingerprint = Fingerprint(
    filters = listOf(string("authorize.compact?client_id"))
)

internal val getBearerTokenFingerprint = Fingerprint(
    filters = listOf(string("Basic"))
)

internal val getUserAgentFingerprint = Fingerprint(
    filters = listOf(string("android:com.laurencedawson.reddit_sync"))
)

internal val imgurImageAPIFingerprint = Fingerprint(
    filters = listOf(string("https://imgur-apiv3.p.rapidapi.com/3/image"))
)
