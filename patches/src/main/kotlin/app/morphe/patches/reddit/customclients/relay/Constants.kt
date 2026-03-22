package app.morphe.patches.reddit.customclients.relay

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val RelayCompatible = arrayOf(
    Compatibility(
        name = "Relay for Reddit Free",
        packageName = "free.reddit.news",
        targets = listOf(AppTarget(version = "10.2.40"))
    ),
    Compatibility(
        name = "Relay for Reddit",
        packageName = "reddit.news",
        targets = listOf(AppTarget(version = "10.2.40"))
    )
)

