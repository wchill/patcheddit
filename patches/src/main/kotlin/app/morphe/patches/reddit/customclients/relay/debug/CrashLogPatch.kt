/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.relay.debug

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.crashLogPatch

@Suppress("unused")
val hookExceptionHandler = crashLogPatch(
    extensionPatch = ExtensionPatches.Relay,
    compatible = AppCompatibility.Relay,
)
