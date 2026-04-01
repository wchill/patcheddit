/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.api

import app.morphe.patcher.Fingerprint

internal val buildUserAgentFingerprint = Fingerprint(
    strings = listOf("%s:%s:%s (by /u/%s)")
)

internal val getClientIdFingerprint = Fingerprint(
    definingClass = "Credentials;",
    name = "getClientId",
)

internal val redirectUriFingerprint = Fingerprint(
    strings = listOf("http://rubenmayayo.com")
)

internal val shouldOverrideUrlLoadingFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/LoginActivity\$a;",
    name = "shouldOverrideUrlLoading"
)

internal val jrawNewUrlFingerprint = Fingerprint(
    definingClass = "JrawUtils;",
    name = "newUrl",
)
