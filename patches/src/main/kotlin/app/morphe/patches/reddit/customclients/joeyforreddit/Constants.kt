/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.joeyforreddit

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val JoeyForRedditFreeCompatible = Compatibility(
    name = "Joey for Reddit",
    packageName = "o.o.joey",
    targets = listOf(AppTarget(version = null))
)

internal val JoeyForRedditCompatible = arrayOf(
    JoeyForRedditFreeCompatible,
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

