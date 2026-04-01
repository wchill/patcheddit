/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.joeyforreddit.ads

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.joeyforreddit.JoeyForRedditFreeCompatible
import app.morphe.patches.reddit.customclients.joeyforreddit.detection.piracy.disablePiracyDetectionPatch
import app.morphe.util.returnEarly

@Suppress("unused")
val disableAdsPatch = bytecodePatch(
    name = "Disable ads",
    default = true
) {
    dependsOn(disablePiracyDetectionPatch)

    compatibleWith(JoeyForRedditFreeCompatible)

    execute {
        isAdFreeUserFingerprint.method.returnEarly(true)
    }
}
