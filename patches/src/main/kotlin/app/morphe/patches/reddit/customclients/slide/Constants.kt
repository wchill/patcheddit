/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.slide

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val SlideCompatible = arrayOf(
    Compatibility(
        name = "Slide (fork)",
        packageName = "me.edgan.redditslide",
        targets = listOf(AppTarget(version = null))
    )
)

