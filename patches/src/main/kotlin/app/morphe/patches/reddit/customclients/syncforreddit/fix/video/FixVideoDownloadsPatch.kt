package app.morphe.patches.reddit.customclients.syncforreddit.fix.video

import app.morphe.patcher.patch.bytecodePatch

@Deprecated(
    message = "Patch was move to a different package",
    ReplaceWith("app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.video.fixVideoDownloadsPatch")
)
@Suppress("unused")
val fixVideoDownloadsPatch = bytecodePatch {
    dependsOn(app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.video.fixVideoDownloadsPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )
}