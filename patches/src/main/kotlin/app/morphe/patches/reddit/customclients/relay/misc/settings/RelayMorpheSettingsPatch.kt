/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.relay.misc.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheListSettingsPatch

@Suppress("unused")
val relayMorpheSettingsPatch = morpheListSettingsPatch(
    "Relay", ExtensionPatches.Relay, AppCompatibility.Relay,
    Fingerprint(definingClass = "Lreddit/news/preferences/SettingsActivity;",
        name = "onCreate", parameters = listOf("Landroid/os/Bundle;"), returnType = "V"),
)
