package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.BytecodePatch
import app.morphe.patches.all.misc.extension.activityOnCreateExtensionHook
import app.morphe.patches.all.misc.extension.sharedExtensionPatch

object ExtensionPatches {
    private fun hook(extensionName: String, vararg hookedClasses: String): BytecodePatch =
        sharedExtensionPatch(
            extensionName, *hookedClasses.map { activityOnCreateExtensionHook(it) }.toTypedArray()
        )
    internal val BaconReader = hook("baconreader", "Lcom/onelouder/baconreader/BaconReader;")

    internal val Boost = hook("boost", "Lcom/rubenmayayo/reddit/MyApplication;")
    internal val Continuum = hook("continuum", "Lml/docilealligator/infinityforreddit/Infinity;")
    internal val Infinity = hook("infinity", "Lml/docilealligator/infinityforreddit/Infinity;")
    internal val Joey = hook("joey", "Lo/o/joey/MyApplication;")
    internal val RIF = hook("redditisfun", "Lcom/andrewshu/android/reddit/RedditIsFunApplication;")
    internal val Relay = hook("relay", "Lreddit/news/RelayApplication;")
    internal val Slide = hook("slide", "Lme/edgan/redditslide/Reddit;")
    internal val Sync = hook("syncforreddit", "Lcom/laurencedawson/reddit_sync/RedditApplication;")
}
