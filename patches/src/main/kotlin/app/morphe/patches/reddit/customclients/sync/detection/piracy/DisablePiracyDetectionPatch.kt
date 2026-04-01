/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.sync.detection.piracy

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.returnEarly

val disablePiracyDetectionPatch = bytecodePatch(
    description = "Disables detection of modified versions.",
    default = false
) {

    execute {
        // Do not throw an error if the fingerprint is not resolved.
        // This is fine because new versions of the target app do not need this patch.
        piracyDetectionFingerprint.methodOrNull?.returnEarly()
    }
}
