/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.api

import app.morphe.patcher.Fingerprint

internal val getAuthorizationStringFingerprint = Fingerprint(
    strings = listOf("https://ssl.reddit.com/api/v1/authorize.compact?client_id=Q43fSpTe8LckEg&response_type=code&state=")
)

internal val getBearerTokenFingerprint = Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("authorize.compact?client_id")
    ),
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
