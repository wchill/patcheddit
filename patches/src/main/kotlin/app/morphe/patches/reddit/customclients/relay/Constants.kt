/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.relay

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val RelayCompatible = arrayOf(
    Compatibility(
        name = "Relay for Reddit Free",
        packageName = "free.reddit.news",
        targets = listOf(AppTarget(version = "10.2.40"))
    ),
    Compatibility(
        name = "Relay for Reddit",
        packageName = "reddit.news",
        targets = listOf(AppTarget(version = "10.2.40"))
    )
)

