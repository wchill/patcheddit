/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.patch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.all.misc.PatchCategory

internal fun redditApiPatch(spoofClient: Patch, vararg fixes: Patch) = patch(
    name = "Reddit API fixes",
    description = "Restores Reddit API access and login with the fixes required by this app.",
) {
    category(PatchCategory.LOGIN)
    compatibleWith(*checkNotNull(spoofClient.compatibility).toTypedArray())
    dependsOn(spoofClient, *fixes)
    // Share the actual options so dependencies see overrides before they execute.
    spoofClient.options.values.forEach { it() }
}

internal fun legacySpoofClientPatch(apiPatch: Patch) = patch(
    name = "Spoof client",
    description = "Restores saved OAuth options from before 2.3.0. Includes Reddit API fixes. " +
        "Launch the updated app once to save these values in its API settings before deselecting this patch.",
    default = false,
) {
    category(PatchCategory.LOGIN)
    compatibleWith(*checkNotNull(apiPatch.compatibility).toTypedArray())
    dependsOn(apiPatch, morpheSettingsContextPatch)
    apiPatch.options.values.forEach { it() }
    execute {
        Fingerprint(
            definingClass = OAUTH_SETTINGS_DESCRIPTOR,
            name = "shouldMigratePatchOptions",
        ).method.returnEarly(true)
    }
}
