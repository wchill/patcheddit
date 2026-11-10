/*
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.gallery

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheBytecodePatch

@Suppress("unused")
val fixFailedGalleryImagesPatch = morpheBytecodePatch(
    name = "Fix missing gallery images",
    description = "Rebuilds gallery images that Reddit's API now reports as \"failed\" with no URL " +
        "(\"Glide failed to grab this image\"), using the original file on i.redd.it.",
    default = true,
    extensionPatch = ExtensionPatches.Sync,
) { setting ->
    compatibleWith(*AppCompatibility.SyncForReddit)
    dependsOn(ExtensionPatches.Sync)

    execute {
        setting.returnEnabled(Fingerprint(
            definingClass = "Lapp/morphe/extension/shared/fixes/api/FailedGalleryMetadataPatch;",
            name = "isPatchIncluded",
        ).method)
    }
}
