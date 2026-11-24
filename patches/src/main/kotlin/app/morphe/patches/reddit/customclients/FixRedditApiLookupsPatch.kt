/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.returnEarly

fun fixRedditApiLookupsPatch(
    extensionPatch: Patch,
    compatible: Array<Compatibility>,
    extraDependency: Patch? = null,
) = patch(
    description = "Fixes blocked post, comment, and community info requests.",
    default = true,
) {
    compatibleWith(*compatible)
    dependsOn(extensionPatch)
    extraDependency?.let { dependsOn(it) }

    execute {
        Fingerprint(
            definingClass = "Lapp/morphe/extension/shared/fixes/api/RedditInfoResponsePatch;",
            name = "isPatchIncluded",
        ).method.returnEarly(true)
    }
}
