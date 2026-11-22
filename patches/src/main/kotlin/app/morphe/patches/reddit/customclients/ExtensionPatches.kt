package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.patch
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.boostExtensionPatch
import app.morphe.patches.reddit.customclients.http.httpClientExtensionPatch
import app.morphe.patches.reddit.customclients.misc.extension.clientExtensionPatch
import app.morphe.patches.reddit.customclients.redditisfun.fix.platinum.platinumAdSlotSafeguardPatch

object ExtensionPatches {
    private fun hook(extensionName: String, vararg hookedClasses: String): Patch =
        clientExtensionPatch(extensionName, hookedClasses)
    private fun httpHook(extensionName: String, vararg hookedClasses: String): Patch =
        httpClientExtensionPatch(extensionName, *hookedClasses)

    internal val BaconReader = httpHook("baconreader", "Lcom/onelouder/baconreader/BaconReader;")
    internal val Boost = boostExtensionPatch
    internal val Infinity = hook("infinity", "Lml/docilealligator/infinityforreddit/Infinity;")
    internal val Joey = httpHook("joey", "Lo/o/joey/MyApplication;")
    internal val RIF = patch(default = false) {
        dependsOn(
            platinumAdSlotSafeguardPatch,
            httpHook("redditisfun", "Lcom/andrewshu/android/reddit/RedditIsFunApplication;"),
        )
    }
    internal val Relay = httpHook("relay", "Lreddit/news/RelayApplication;")
    internal val Sync = httpHook("syncforreddit", "Lcom/laurencedawson/reddit_sync/RedditApplication;")
    internal val SyncForLemmy = clientExtensionPatch(null, emptyArray())
}
