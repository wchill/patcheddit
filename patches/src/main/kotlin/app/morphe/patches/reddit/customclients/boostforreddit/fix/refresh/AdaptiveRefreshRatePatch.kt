/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.refresh

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.adaptiveRefreshRatePatch
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

@Suppress("unused")
val preferHighRefreshRatePatch = adaptiveRefreshRatePatch(
    extensionPatch = ExtensionPatches.Boost,
    compatible = AppCompatibility.Boost,
    applicationOnCreate = BoostApi.MyApplication.onCreate.fingerprint(),
    preferenceKey = "morphe_boost_prefer_high_refresh_rate",
    description = "Requests a high display refresh rate for Boost on adaptive-refresh devices.",
)
