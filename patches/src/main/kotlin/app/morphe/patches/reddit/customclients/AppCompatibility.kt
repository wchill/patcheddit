package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object AppCompatibility {
    internal val Boost = arrayOf(
        Compatibility(
            name = "Boost for Reddit",
            packageName = "com.rubenmayayo.reddit",
            targets = listOf(AppTarget(version = "1.14.1"))
        )
    )
}
