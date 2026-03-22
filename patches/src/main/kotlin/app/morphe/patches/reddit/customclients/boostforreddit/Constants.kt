package app.morphe.patches.reddit.customclients.boostforreddit

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val BoostCompatible = arrayOf(
    Compatibility(
        name = "Boost for Reddit",
        packageName = "com.rubenmayayo.reddit",
        targets = listOf(AppTarget(version = "1.12.12"))
    )
)