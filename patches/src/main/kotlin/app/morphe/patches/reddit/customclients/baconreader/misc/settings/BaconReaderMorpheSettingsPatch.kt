/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.baconreader.misc.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheListSettingsPatch

@Suppress("unused")
val baconReaderMorpheSettingsPatch = morpheListSettingsPatch(
    "BaconReader", ExtensionPatches.BaconReader, AppCompatibility.BaconReader,
    Fingerprint(definingClass = "Lcom/onelouder/baconreader/SettingsMenuFragment;",
        name = "initAdapter", parameters = emptyList(), returnType = "V"),
)
