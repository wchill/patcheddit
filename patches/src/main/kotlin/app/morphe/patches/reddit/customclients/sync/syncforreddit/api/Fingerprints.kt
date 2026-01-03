package app.morphe.patches.reddit.customclients.sync.syncforreddit.api

import app.morphe.patcher.fingerprint

internal val getAuthorizationStringFingerprint = fingerprint {
    strings("authorize.compact?client_id")
}

internal val getBearerTokenFingerprint = fingerprint {
    strings("Basic")
}

internal val getUserAgentFingerprint = fingerprint {
    strings("android:com.laurencedawson.reddit_sync")
}

internal val imgurImageAPIFingerprint = fingerprint {
    strings("https://imgur-apiv3.p.rapidapi.com/3/image")
}
