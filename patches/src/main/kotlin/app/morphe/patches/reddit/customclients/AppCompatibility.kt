package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object AppCompatibility {
    internal val BaconReader = arrayOf(
        Compatibility(
            name = "BaconReader",
            packageName = "com.onelouder.baconreader",
            targets = listOf(AppTarget(version = "6.1.4"))
        ),
        Compatibility(
            name = "BaconReader Premium",
            packageName = "com.onelouder.baconreader.premium",
            targets = listOf(AppTarget(version = "6.1.4"))
        )
    )

    internal val Boost = arrayOf(
        Compatibility(
            name = "Boost for Reddit",
            packageName = "com.rubenmayayo.reddit",
            targets = listOf(AppTarget(version = "1.12.12"))
        )
    )

    internal val Infinity = arrayOf(
        Compatibility(
            name = "Infinity for Reddit+",
            packageName = "ml.docilealligator.infinityforreddit.plus",
            targets = listOf(AppTarget(version = null))
        ),
        Compatibility(
            name = "Infinity for Reddit (Patreon)",
            packageName = "ml.docilealligator.infinityforreddit.patreon",
            targets = listOf(AppTarget(version = null))
        )
    )

    internal val JoeyForRedditFree = Compatibility(
        name = "Joey for Reddit",
        packageName = "o.o.joey",
        targets = listOf(AppTarget(version = null))
    )

    internal val JoeyForReddit = arrayOf(
        JoeyForRedditFree,
        Compatibility(
            name = "Joey for Reddit Pro",
            packageName = "o.o.joey.pro",
            targets = listOf(AppTarget(version = null))
        ),
        Compatibility(
            name = "Joey for Reddit Dev",
            packageName = "o.o.joey.dev",
            targets = listOf(AppTarget(version = null))
        )
    )

    internal val RedditIsFunFree =
        Compatibility(
            name = "rif is fun",
            packageName = "com.andrewshu.android.reddit",
            targets = listOf(AppTarget(version = "5.6.22"))
        )

    internal val RedditIsFun = arrayOf(
        RedditIsFunFree,
        Compatibility(
            name = "rif is fun golden platinum",
            packageName = "com.andrewshu.android.redditdonation",
            targets = listOf(AppTarget(version = "5.6.22"))
        )
    )

    internal val Relay = arrayOf(
        Compatibility(
            name = "Relay for Reddit",
            packageName = "free.reddit.news",
            targets = listOf(AppTarget(version = "10.2.40"))
        ),
        Compatibility(
            name = "Relay for Reddit Pro",
            packageName = "reddit.news",
            targets = listOf(AppTarget(version = "10.2.40"))
        )
    )

    internal val SyncForRedditFree = Compatibility(
        name = "Sync for Reddit",
        packageName = "com.laurencedawson.reddit_sync",
        targets = listOf(AppTarget(version = "v23.06.30-13:39"))
    )

    internal val SyncForLemmy = Compatibility(
        name = "Sync for Lemmy",
        packageName = "io.syncapps.lemmy_sync",
        targets = listOf(AppTarget(version = null))
    )

    internal val SyncForReddit = arrayOf(
        SyncForRedditFree,
        Compatibility(
            name = "Sync for Reddit Pro",
            packageName = "com.laurencedawson.reddit_sync.pro",
            targets = listOf(AppTarget(version = null))
        ),
        Compatibility(
            name = "Sync for Reddit Dev",
            packageName = "com.laurencedawson.reddit_sync.dev",
            targets = listOf(AppTarget(version = null))
        )
    )

}
