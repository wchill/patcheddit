/*
 * Copyright 2026 wchill.
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

fun fixRedditApiLookupsPatch(
    extensionPatch: Patch<*>,
    compatible: Array<Compatibility>,
) = bytecodePatch(
    name = "Fix Reddit API lookups",
    description = "Fixes blocked post, comment, and community info requests.",
    default = true,
) {
    category(PatchCategory.LOGIN)
    compatibleWith(*compatible)
    dependsOn(extensionPatch)

    execute {
        Fingerprint(
            definingClass = "Lapp/morphe/extension/shared/fixes/api/RedditInfoResponsePatch;",
            name = "isPatchIncluded",
        ).method.returnEarly(true)
    }
}
