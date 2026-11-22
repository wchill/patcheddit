/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.returnEarly

@JvmOverloads
fun fixMoreCommentsPatch(
    extensionPatch: Patch,
    compatible: Array<Compatibility>,
    default: Boolean = true,
) = patch(
    description = "Fixes 403 error when loading child comments.",
    default = default,
) {
    compatibleWith(*compatible)
    dependsOn(extensionPatch)

    execute {
        Fingerprint(
            definingClass =
                "Lapp/morphe/extension/shared/fixes/comments/MoreChildrenResponsePatch;",
            name = "isPatchIncluded",
        ).method.returnEarly(true)
    }
}
