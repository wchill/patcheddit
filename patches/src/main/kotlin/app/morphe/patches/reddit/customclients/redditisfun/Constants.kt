/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val RedditIsFunFreeCompatible =
    Compatibility(
        name = "rif is fun",
        packageName = "com.andrewshu.android.reddit",
        targets = listOf(AppTarget(version = "5.6.22"))
    )

internal val RedditIsFunCompatible = arrayOf(
    RedditIsFunFreeCompatible,
    Compatibility(
        name = "rif is fun golden platinum",
        packageName = "com.andrewshu.android.redditdonation",
        targets = listOf(AppTarget(version = "5.6.22"))
    )
)

