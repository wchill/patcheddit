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
import app.morphe.patches.reddit.customclients.addMorpheApkSettings
import app.morphe.patches.reddit.customclients.morpheSettingsContextPatch
import app.morphe.patches.reddit.customclients.morpheSettingsResourcesPatch
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.util.ResourceGroup
import org.w3c.dom.Element

internal fun ResourcePatchContext.addBoostSettings(resourceName: String, beforeTitle: String? = null) {
    document("res/xml/$resourceName.xml").use { source ->
        document("res/xml/morphe_boost_settings_skeleton.xml").use { target ->
            val before = beforeTitle?.let { title ->
                val categories = target.getElementsByTagName("PreferenceCategory")
                (0 until categories.length).map { categories.item(it) as Element }
                    .single { it.getAttribute("android:title") == title }
            }
            target.documentElement.insertBefore(target.importNode(source.documentElement, true), before)
        }
    }
}

internal val boostMorpheSettingsResourcesPatch = resourcePatch(
    default = false
) {
    dependsOn(
        morpheSettingsResourcesPatch,
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
                val entry = target.importNode(source.documentElement, true) as Element
                entry.getElementsByTagName("intent").item(0).let { it as Element }
                    .setAttribute("android:targetPackage", "com.rubenmayayo.reddit")
                root.insertBefore(entry, root.firstChild)
            }
        }

        document("AndroidManifest.xml").use { manifest ->
            val application = checkNotNull(manifest.getElementsByTagName("application").item(0) as? Element) {
                "AndroidManifest.xml has no application element."
            }
            val activity = "app.morphe.extension.boost.settings.BoostSettingsActivity"
            val activities = application.getElementsByTagName("activity")
            require((0 until activities.length).none {
                (activities.item(it) as Element).getAttribute("android:name") == activity
            }) { "Boost Morphe settings activity is already declared." }
            application.appendChild(manifest.createElement("activity").apply {
                setAttribute("android:name", activity)
                setAttribute("android:exported", "false")
            })
        }
    }

    finalize {
        addMorpheApkSettings()
    }
}

@Suppress("unused")
val boostMorpheSettingsPatch = resourcePatch(
    name = "Boost Morphe settings",
    description = "Adds a native-style Morphe section while keeping Boost's original settings screens.",
    default = true,
) {
    category(PatchCategory.INTERFACE)
    dependsOn(ExtensionPatches.Boost, boostMorpheSettingsResourcesPatch, morpheSettingsContextPatch)
    compatibleWith(*AppCompatibility.Boost)
}
