package app.morphe.patches.reddit.customclients.redditisfun

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val RedditIsFunCompatible = arrayOf(
    Compatibility(
        name = "Reddit is Fun",
        packageName = "com.andrewshu.android.reddit",
        targets = listOf(AppTarget(version = "5.6.22"))
    )
)

