/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.baconreader.misc.extension.hooks

import app.morphe.patcher.Fingerprint
import app.morphe.patches.all.misc.extension.ExtensionHook

internal val initHook = ExtensionHook(
    Fingerprint(
        definingClass = "Lcom/onelouder/baconreader/BaconReader;",
        name = "onCreate",
    )
)
