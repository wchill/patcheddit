/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly

internal fun fixFailedGalleryImagesPatch(
    extensionPatch: Patch,
    compatible: Array<Compatibility>,
    additional: BytecodePatchContext.() -> Unit = {},
) = bytecodePatch(
    description = "Restores Reddit gallery images that the API reports as failed when opening posts.",
) {
    compatibleWith(*compatible)
    dependsOn(extensionPatch)

    execute {
        Fingerprint(
            definingClass = "Lapp/morphe/extension/shared/fixes/api/FailedGalleryMetadataPatch;",
            name = "isPatchIncluded",
        ).method.returnEarly(true)
        additional()
    }
}
