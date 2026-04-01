/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.joeyforreddit.detection.piracy

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.returnEarly

val disablePiracyDetectionPatch = bytecodePatch(
    default = false,
) {
    execute {
        piracyDetectionFingerprint.method.returnEarly()
    }
}
