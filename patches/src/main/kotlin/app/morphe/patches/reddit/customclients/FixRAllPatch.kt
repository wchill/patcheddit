/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly

fun fixRAllPatch(
    extensionPatch: Patch,
    compatible: Array<Compatibility>
) = bytecodePatch(
    description = "Loads /r/all through Reddit's .json endpoint so the feed shows posts.",
    default = true,
) {
    compatibleWith(*compatible)

    dependsOn(
        extensionPatch,
    )

    execute {
        Fingerprint(
            definingClass = "Lapp/morphe/extension/shared/fixes/feed/RAllPatch;",
            name = "isPatchIncluded",
        ).method.returnEarly(true)
    }
}
