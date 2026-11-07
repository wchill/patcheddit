/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patches.all.misc.PatchCategory

fun fixRedditApiLookupsPatch(
    extensionPatch: Patch<*>,
    compatible: Array<Compatibility>,
) = morpheBytecodePatch(
    name = "Fix Reddit API lookups",
    description = "Fixes blocked post, comment, and community info requests.",
    default = true,
    extensionPatch = extensionPatch,
    category = PatchCategory.LOGIN,
) { setting ->
    compatibleWith(*compatible)
    dependsOn(extensionPatch)

    execute {
        setting.returnEnabled(Fingerprint(
            definingClass = "Lapp/morphe/extension/shared/fixes/api/RedditInfoResponsePatch;",
            name = "isPatchIncluded",
        ).method)
    }
}
