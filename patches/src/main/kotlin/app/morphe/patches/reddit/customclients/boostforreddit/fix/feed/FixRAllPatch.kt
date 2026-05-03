/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */
package app.morphe.patches.reddit.customclients.boostforreddit.fix.feed

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.patches.reddit.customclients.fixRAllPatch

@Suppress("unused")
val fixRAll = fixRAllPatch(
    extensionPatch = interceptHttpRequests,
    compatible = AppCompatibility.Boost,
)
