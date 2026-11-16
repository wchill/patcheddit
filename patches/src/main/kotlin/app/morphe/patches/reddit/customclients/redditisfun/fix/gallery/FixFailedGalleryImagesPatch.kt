/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.gallery

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixFailedGalleryImagesPatch

@Suppress("unused")
val fixFailedGalleryImagesPatch = fixFailedGalleryImagesPatch(
    extensionPatch = ExtensionPatches.RIF,
    compatible = AppCompatibility.RedditIsFun,
)
