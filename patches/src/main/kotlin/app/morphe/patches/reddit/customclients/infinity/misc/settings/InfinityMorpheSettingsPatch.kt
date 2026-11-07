/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.infinity.misc.settings

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morphePreferencesPatch

@Suppress("unused")
val infinityMorpheSettingsPatch = morphePreferencesPatch(
    "Infinity", ExtensionPatches.Infinity, AppCompatibility.Infinity,
    "main_preferences", nativeStyle = true,
)
