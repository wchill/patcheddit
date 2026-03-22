package app.morphe.patches.reddit.customclients.joeyforreddit

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val JoeyForRedditFreeCompatible = Compatibility(
    name = "Joey for Reddit",
    packageName = "o.o.joey",
    targets = listOf(AppTarget(version = null))
)

internal val JoeyForRedditCompatible = arrayOf(
    JoeyForRedditFreeCompatible,
    Compatibility(
        name = "Joey for Reddit",
        packageName = "o.o.joey.pro",
        targets = listOf(AppTarget(version = null))
    ),
    Compatibility(
        name = "Joey for Reddit",
        packageName = "o.o.joey.dev",
        targets = listOf(AppTarget(version = null))
    )
)

