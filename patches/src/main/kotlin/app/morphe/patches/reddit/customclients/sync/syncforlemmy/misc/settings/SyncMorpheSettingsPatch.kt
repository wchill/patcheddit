/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforlemmy.misc.settings

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morphePreferencesPatch

@Suppress("unused")
val syncMorpheSettingsPatch = morphePreferencesPatch(
    "Sync for Lemmy", ExtensionPatches.SyncForLemmy, arrayOf(AppCompatibility.SyncForLemmy), "cat_root",
)
