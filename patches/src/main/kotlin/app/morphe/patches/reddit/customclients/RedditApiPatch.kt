/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.PatchCategory

internal fun redditApiPatch(spoofClient: Patch<*>, vararg fixes: Patch<*>) = bytecodePatch(
    name = "Reddit API fixes",
    description = "Restores Reddit API access and login with the fixes required by this app.",
) {
    category(PatchCategory.LOGIN)
    compatibleWith(*checkNotNull(spoofClient.compatibility).toTypedArray())
    dependsOn(spoofClient, *fixes)
    // Share the actual options so dependencies see overrides before they execute.
    spoofClient.options.values.forEach { it() }
}
