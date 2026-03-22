package app.morphe.patches.reddit.customclients.baconreader

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val BaconReaderCompatible = arrayOf(
    Compatibility(
        name = "BaconReader",
        packageName = "com.onelouder.baconreader",
        targets = listOf(AppTarget(version = null))
    ),
    Compatibility(
        name = "BaconReader",
        packageName = "com.onelouder.baconreader.premium",
        targets = listOf(AppTarget(version = null))
    )
)