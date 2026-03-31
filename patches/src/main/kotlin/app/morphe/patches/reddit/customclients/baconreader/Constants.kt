/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.baconreader

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val BaconReaderCompatible = arrayOf(
    Compatibility(
        name = "BaconReader",
        packageName = "com.onelouder.baconreader",
        targets = listOf(AppTarget(version = null))
    ),
    Compatibility(
        name = "BaconReader Premium",
        packageName = "com.onelouder.baconreader.premium",
        targets = listOf(AppTarget(version = null))
    )
)