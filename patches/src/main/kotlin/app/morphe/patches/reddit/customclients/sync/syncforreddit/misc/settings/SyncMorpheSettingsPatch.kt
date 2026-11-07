/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.misc.settings

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morphePreferencesPatch

@Suppress("unused")
val syncMorpheSettingsPatch = morphePreferencesPatch(
    "Sync", ExtensionPatches.Sync, AppCompatibility.SyncForReddit, "cat_root",
)
