/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.gallery

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixFailedGalleryImagesPatch

// Parser hook adapted from https://github.com/MojiRS/morphe-rif-patches (GPL-3.0).
private val galleryParser = Fingerprint(
    returnType = "Lcom/andrewshu/android/reddit/browser/redditgallery/GalleryThreadThing;",
    parameters = listOf("Ljava/io/InputStream;"),
)

@Suppress("unused")
val fixFailedGalleryImagesPatch = fixFailedGalleryImagesPatch(
    extensionPatch = ExtensionPatches.RIF,
    compatible = AppCompatibility.RedditIsFun,
) {
    galleryParser.method.addInstructions(
        0,
        """
            invoke-static { p1 }, Lapp/morphe/extension/redditisfun/GalleryRepair;->repairGallery(Ljava/io/InputStream;)Ljava/io/InputStream;
            move-result-object p1
        """,
    )
}
