/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.ads

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.util.returnEarly

@Suppress("unused")
val disableAdsPatch = bytecodePatch(
    name = "Disable ads",
    default = true
) {
    compatibleWith(*BoostCompatible)

    execute {
        arrayOf(maxMediationFingerprint, admobMediationFingerprint).forEach { fingerprint ->
            fingerprint.method.returnEarly()
        }
    }
}
