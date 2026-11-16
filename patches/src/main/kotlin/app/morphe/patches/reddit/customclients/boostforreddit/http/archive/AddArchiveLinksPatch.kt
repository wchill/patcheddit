/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http.archive

import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.boostforreddit.http.links.boostLinkMenuPatch

internal const val SETTINGS_UTILS_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boost/utils/SettingsUtils;"

@Suppress("unused")
val addArchiveLinks = morpheBytecodePatch(
    name = "Add archive links to context menu",
    description = "Adds archive links to the link context menu, with a setting for each source.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.LINKS,
    optionsResource = "morphe_boost_archive_options",
    resourceDirectory = "boost",
) { setting ->
    dependsOn(boostLinkMenuPatch)
    compatibleWith(*AppCompatibility.Boost)
    execute {
        setting.returnEnabled(mutableClassDefBy(SETTINGS_UTILS_EXTENSION_CLASS_DESCRIPTOR)
            .methods.single { it.name == "isArchiveMenuEnabled" })
    }
}
