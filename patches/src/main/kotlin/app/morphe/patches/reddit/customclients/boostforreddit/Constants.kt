/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val BoostCompatible = arrayOf(
    Compatibility(
        name = "Boost for Reddit",
        packageName = "com.rubenmayayo.reddit",
        targets = listOf(AppTarget(version = "1.12.12"))
    )
)