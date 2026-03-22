package app.morphe.patches.reddit.customclients.continuum

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val ContinuumCompatible = arrayOf(
    Compatibility(
        name = "Continuum",
        packageName = "org.cygnusx1.continuum",
        targets = listOf(AppTarget(version = null))
    )
)
