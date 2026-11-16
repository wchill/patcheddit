/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.relay.fix.sidebar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches

internal val fixSidebarPatch = bytecodePatch(
    description = "Restores subreddit sidebars when Reddit omits display counters.",
) {
    compatibleWith(*AppCompatibility.Relay)
    dependsOn(ExtensionPatches.Relay)
    execute {
        Fingerprint(
            definingClass = "Lapp/morphe/extension/shared/fixes/api/SubredditAboutResponsePatch;",
            name = "isPatchIncluded",
        ).method.returnEarly(true)
    }
}
