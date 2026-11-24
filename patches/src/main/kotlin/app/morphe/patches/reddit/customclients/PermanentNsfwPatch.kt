/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.patch
import app.morphe.patches.reddit.customclients.baconreader.misc.settings.baconReaderMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.boostforreddit.misc.settings.boostMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.infinity.misc.settings.infinityMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.joey.misc.settings.joeyMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.redditisfun.misc.settings.rifMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.relay.misc.settings.relayMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.misc.settings.syncMorpheSettingsPatch
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val CATEGORY = "Content filtering"
private const val TEST_RECEIVER = "app.morphe.extension.shared.settings.MatureContentLockTestReceiver"

private fun permanentNsfwOption(menu: Patch, compatible: Array<Compatibility>) = patch(
    name = "Permanently disable NSFW",
    description = "Adds an NSFW lock that cannot be turned off in app settings. Does not activate it automatically.",
    default = false,
) {
    dependsOn(menu)
    category(CATEGORY)
    compatibleWith(*compatible)

    execute {
        val packageName = document("AndroidManifest.xml").use { manifest ->
            registerMatureContentLockTestReceiver(manifest)
            manifest.documentElement.getAttribute("package")
        }
        document("res/xml/${morpheMenuResource(packageName)}.xml").use { target ->
            val root = target.documentElement
            val sections = root.getElementsByTagName("PreferenceScreen")
            val section = (0 until sections.length).map { sections.item(it) as Element }
                .singleOrNull { it.getAttribute("android:key") == "morphe_category_content_filtering" }
                ?: target.createElement("PreferenceScreen").apply {
                    setAttribute("android:key", "morphe_category_content_filtering")
                    setAttribute("android:title", "@string/morphe_content_filtering_title")
                    setAttribute("android:persistent", "false")
                    root.appendChild(this)
                }
            section.appendChild(target.createElement("CheckBoxPreference").apply {
                setAttribute("android:key", "morphe_enable_mature_content_lock")
                setAttribute("android:title", "@string/morphe_mature_content_lock_title")
                setAttribute("android:summary", "@string/morphe_mature_content_lock_summary")
                setAttribute("android:persistent", "false")
            })
        }
    }
}

internal fun registerMatureContentLockTestReceiver(manifest: Document) {
    val application = checkNotNull(manifest.getElementsByTagName("application").item(0) as? Element) {
        "AndroidManifest.xml has no application element."
    }
    val receivers = application.getElementsByTagName("receiver")
    require((0 until receivers.length).none {
        (receivers.item(it) as Element).getAttribute("android:name") == TEST_RECEIVER
    }) { "The app already declares the mature content lock test receiver." }
    application.appendChild(manifest.createElement("receiver").apply {
        setAttribute("android:name", TEST_RECEIVER)
        setAttribute("android:exported", "true")
        // ADB shell holds this signature-only permission; ordinary apps cannot obtain it.
        setAttribute("android:permission", "android.permission.INJECT_EVENTS")
    })
}

val baconReaderNsfwOption = permanentNsfwOption(baconReaderMorpheSettingsPatch, AppCompatibility.BaconReader)
val boostNsfwOption = permanentNsfwOption(boostMorpheSettingsPatch, AppCompatibility.Boost)
val infinityNsfwOption = permanentNsfwOption(infinityMorpheSettingsPatch, AppCompatibility.Infinity)
val joeyNsfwOption = permanentNsfwOption(joeyMorpheSettingsPatch, AppCompatibility.JoeyForReddit)
val rifNsfwOption = permanentNsfwOption(rifMorpheSettingsPatch, AppCompatibility.RedditIsFun)
val relayNsfwOption = permanentNsfwOption(relayMorpheSettingsPatch, AppCompatibility.Relay)
val syncNsfwOption = permanentNsfwOption(syncMorpheSettingsPatch, AppCompatibility.SyncForReddit)
