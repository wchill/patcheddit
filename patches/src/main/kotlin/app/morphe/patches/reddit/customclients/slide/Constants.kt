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

