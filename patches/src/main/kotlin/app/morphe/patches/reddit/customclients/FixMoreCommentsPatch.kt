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
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.util.returnEarly

fun fixMoreCommentsPatch(
    extensionPatch: Patch<*>,
    compatible: Array<Compatibility>,
    default: Boolean = true,
) = bytecodePatch(
    name = "Fix loading more comments",
    description = "Fixes 403 error when loading child comments.",
    default = default,
) {
    category(PatchCategory.COMMENTS)
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
