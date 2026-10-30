/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.redgifs

import app.morphe.patcher.Fingerprint

// Method that requests a RedGifs token from the removed /v2/oauth/client endpoint.
internal object RedgifsTokenFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    strings = listOf("https://api.redgifs.com/v2/oauth/client"),
)
