/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.debug

import app.morphe.patcher.Fingerprint

internal val exceptionHandlerFingerprint = Fingerprint(
    custom = { method, _ ->
        method.definingClass == "Lhe/h0;" && method.name == "j0"
    }
)

internal val timberFingerprint = Fingerprint(
    custom = { method, _ ->
        method.definingClass == "Lch/a;" && method.name == "a"
    }
)
