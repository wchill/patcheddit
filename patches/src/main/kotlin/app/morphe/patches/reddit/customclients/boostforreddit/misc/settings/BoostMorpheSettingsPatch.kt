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
import org.w3c.dom.Document

internal fun ResourcePatchContext.addBoostSettings(resourceName: String, beforeTitle: String? = null) {
    document("res/xml/$resourceName.xml").use { source ->
        document("res/xml/morphe_boost_settings_skeleton.xml").use { target ->
            addBoostSettings(target, source, beforeTitle)
        }
    }
}

internal fun addBoostSettings(target: Document, source: Document, beforeTitle: String? = null) {
    val section = source.documentElement
    val key = section.getAttribute("android:key")
    require(key.isNotBlank()) { "Boost settings sections must have keys." }
    val nodes = target.documentElement.childNodes
    val sections = (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    val matches = sections.filter { it.getAttribute("android:key") == key }
    require(matches.size <= 1) { "Duplicate Boost settings section: $key" }
    val existing = matches.singleOrNull()
    if (existing != null) {
        val entries = section.childNodes
        for (index in 0 until entries.length) {
            existing.appendChild(target.importNode(entries.item(index), true))
        }
    } else {
        val before = beforeTitle?.let { title ->
            sections.single { it.getAttribute("android:title") == title }
        }
        target.documentElement.insertBefore(target.importNode(section, true), before)
    }
}

internal fun groupBoostLinkSettings(target: Document) {
    val nodes = target.documentElement.childNodes
    val links = (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
        .singleOrNull { it.getAttribute("android:key") == "morphe_category_links" } ?: return
    val groups = linkedMapOf(
        "general" to "General",
        "archives" to "Archive links",
        "services" to "Enabled archive services",
    ).mapValues { (key, title) ->
        target.createElement("PreferenceCategory").apply {
            setAttribute("android:key", "morphe_boost_links_$key")
            setAttribute("android:title", title)
        }
    }
    val children = links.childNodes
    val entries = (0 until children.length).mapNotNull { children.item(it) as? Element }
    for (entry in entries) {
        val key = entry.getAttribute("android:key")
        val group = when {
            key == "morphe_patch_add_archive_links_to_context_menu" ||
                key == "morphe_boost_archive_strip_query_parameters" -> "archives"
            key.startsWith("morphe_boost_archive_") -> "services"
            else -> "general"
        }
        groups.getValue(group).appendChild(entry)
    }
    for (group in groups.values) {
        if (group.hasChildNodes()) links.appendChild(group)
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
        document("res/xml/morphe_boost_settings_skeleton.xml").use(::groupBoostLinkSettings)
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
