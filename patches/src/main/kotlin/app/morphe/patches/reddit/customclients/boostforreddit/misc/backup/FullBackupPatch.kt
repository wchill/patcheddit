/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.misc.backup

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import org.w3c.dom.Element

private const val EXTENSION = "Lapp/morphe/extension/boost/backup/BoostFullBackup;"

private val keepBoostStoresInOneProcess = resourcePatch(default = false) {
    execute {
        document("AndroidManifest.xml").use { manifest ->
            val receivers = manifest.getElementsByTagName("receiver")
            val alarm = (0 until receivers.length).map { receivers.item(it) as Element }.single {
                it.getAttribute("android:name") == "com.rubenmayayo.reddit.services.MyAlarmReceiver"
            }
            // The alarm receiver can open Boost's databases via MyApplication. Give it the
            // same process so startup can replace the stores before any caller opens them.
            check(alarm.getAttribute("android:process") == ":remote") { "Unexpected Boost alarm process" }
            alarm.removeAttribute("android:process")
        }
    }
}

private val addFullBackupRows = resourcePatch(default = false) {
    execute {
        document("res/layout/content_backup.xml").use { layout ->
            val container = layout.documentElement.childElements().single()
            val rows = container.childElements()
            val explanation = rows.single { it.hasId("backup_explanation") }
            val actions = layout.createElement("LinearLayout").apply {
                setAttribute("android:tag", "morphe_full_backup_actions")
                setAttribute("android:orientation", "vertical")
                setAttribute("android:layout_width", "match_parent")
                setAttribute("android:layout_height", "wrap_content")
                setAttribute("android:visibility", "gone")
            }
            for ((action, title) in listOf("export" to "Export full backup", "import" to "Import full backup")) {
                val native = rows.single { row -> row.childElements().any { it.hasId("${action}_settings") } }
                val divider = rows[rows.indexOf(native) + 1]
                check(divider.tagName == "com.google.android.material.divider.MaterialDivider") {
                    "Missing native Boost backup divider"
                }
                val row = native.cloneNode(true) as Element
                val clickable = row.childElements().single { it.hasId("${action}_settings") }
                clickable.removeAttribute("android:id")
                clickable.setAttribute("android:tag", "morphe_full_backup_$action")
                clickable.childElements().single { it.tagName == "TextView" }
                    .setAttribute("android:text", title)
                actions.appendChild(row)
                actions.appendChild(divider.cloneNode(true))
            }
            container.insertBefore(actions, explanation)
        }
    }
}

private fun Element.childElements(): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

private fun Element.hasId(name: String) = getAttribute("android:id").substringAfterLast('/') == name

@Suppress("unused")
val boostFullBackupPatch = bytecodePatch(
    name = "Full Boost backup",
    description = "Adds full backups of local settings, accounts, drafts, and history to Boost's Backup screen.",
) {
    category(PatchCategory.ADVANCED)
    compatibleWith(*AppCompatibility.Boost)
    dependsOn(ExtensionPatches.Boost, keepBoostStoresInOneProcess, addFullBackupRows)

    execute {
        // Content providers and the extension can cache preferences before Application.onCreate.
        BoostApi.MyApplication.attachBaseContext.fingerprint().wrapHook(
            after = "invoke-static {p1}, $EXTENSION->restoreAtStartup(Landroid/content/Context;)V",
        )
        BoostApi.BackupActivity.onCreate.fingerprint().wrapHook(
            after = "invoke-static {p0}, $EXTENSION->addActions(Landroid/app/Activity;)V",
        )
        BoostApi.BackupActivity.onActivityResult.fingerprint().wrapHook(
            after = "invoke-static {p0, p1, p2, p3}, $EXTENSION->onDocumentResult(Landroid/app/Activity;IILandroid/content/Intent;)V",
        )
    }
}
