/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.misc.settings

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.util.ResourceGroup
import org.w3c.dom.Element

internal fun ResourcePatchContext.addBoostSettings(resourceName: String, beforeTitle: String? = null) {
    document("res/xml/$resourceName.xml").use { source ->
        document("res/xml/morphe_boost_settings_skeleton.xml").use { target ->
            // Without a target, insert above About so the attribution notice stays last.
            val categories = target.getElementsByTagName("PreferenceCategory")
            val before = (0 until categories.length).map { categories.item(it) as Element }
                .single { it.getAttribute("android:title") == (beforeTitle ?: "About") }
            target.documentElement.insertBefore(target.importNode(source.documentElement, true), before)
        }
    }
}

internal val boostMorpheSettingsResourcesPatch = resourcePatch(
    default = false
) {
    dependsOn(
        addResourcesPatch(
            "boost",
            ResourceGroup("xml", "morphe_boost_settings_skeleton.xml", "morphe_boost_settings_entry.xml"),
        ),
    )
    compatibleWith(*AppCompatibility.Boost)

    execute {
        document("res/xml/pref_headers_v2.xml").use { target ->
            val root = target.documentElement
            require(root.tagName == "PreferenceScreen") { "Unexpected Boost settings header root" }
            val preferences = root.getElementsByTagName("Preference")
            require((0 until preferences.length).none {
                (preferences.item(it) as Element).getAttribute("android:key") == "morphe_boost_settings_entry"
            }) { "Boost settings already contain a Morphe entry" }
            document("res/xml/morphe_boost_settings_entry.xml").use { source ->
                root.insertBefore(target.importNode(source.documentElement, true), root.firstChild)
            }
        }
    }
}

@Suppress("unused")
val boostMorpheSettingsPatch = resourcePatch(
    name = "Boost Morphe settings",
    description = "Adds a native-style Morphe section while keeping Boost's original settings screens.",
    default = true,
) {
    category(PatchCategory.INTERFACE)
    dependsOn(ExtensionPatches.Boost, boostMorpheSettingsResourcesPatch)
    compatibleWith(*AppCompatibility.Boost)
}
