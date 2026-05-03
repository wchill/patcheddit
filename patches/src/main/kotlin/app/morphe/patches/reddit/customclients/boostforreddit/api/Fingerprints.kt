/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.api

import app.morphe.patcher.Fingerprint

internal object BuildUserAgentFingerprint : Fingerprint(
    strings = listOf("%s:%s:%s (by /u/%s)")
)

internal object GetClientIdFingerprint : Fingerprint(
    definingClass = "Credentials;",
    name = "getClientId",
)

internal object RedirectUriFingerprint : Fingerprint(
    strings = listOf("http://rubenmayayo.com")
)

internal object ShouldOverrideUrlLoadingFingerprint : Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/LoginActivity\$a;",
    name = "shouldOverrideUrlLoading"
)

internal object JrawNewUrlFingerprint : Fingerprint(
    definingClass = "JrawUtils;",
    name = "newUrl",
)
