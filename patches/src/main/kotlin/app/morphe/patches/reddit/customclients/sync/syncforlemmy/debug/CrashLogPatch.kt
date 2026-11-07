/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforlemmy.debug

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.crashLogPatch
import app.morphe.patches.reddit.customclients.sync.syncforlemmy.misc.settings.syncMorpheSettingsPatch

@Suppress("unused")
val hookExceptionHandler = crashLogPatch(
    extensionPatch = ExtensionPatches.SyncForLemmy,
    compatible = arrayOf(AppCompatibility.SyncForLemmy),
    settingsPatch = syncMorpheSettingsPatch,
)
