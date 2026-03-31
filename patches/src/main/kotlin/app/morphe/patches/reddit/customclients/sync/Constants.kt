/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.sync

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val SyncForRedditFreeCompatible = Compatibility(
    name = "Sync for Reddit",
    packageName = "com.laurencedawson.reddit_sync",
    targets = listOf(AppTarget(version = "v23.06.30-13:39"))
)

internal val SyncForLemmyCompatible = Compatibility(
    name = "Sync for Lemmy",
    packageName = "io.syncapps.lemmy_sync",
    targets = listOf(AppTarget(version = null))
)

internal val SyncForRedditCompatible = arrayOf(
    SyncForRedditFreeCompatible,
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

