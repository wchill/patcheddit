/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.returnEarly

fun fixRAllPatch(
    extensionPatch: Array<Patch<*>>,
    compatible: Array<Compatibility>
) = bytecodePatch(
    name = "Fix /r/all",
    default = true
) {
    compatibleWith(*compatible)

    dependsOn(
        *extensionPatch,
    )

    execute {
        Fingerprint(
            definingClass = "Lapp/morphe/extension/shared/fixes/feed/RAllPatch;",
            name = "isPatchIncluded",
        ).method.returnEarly(true)
    }
}
