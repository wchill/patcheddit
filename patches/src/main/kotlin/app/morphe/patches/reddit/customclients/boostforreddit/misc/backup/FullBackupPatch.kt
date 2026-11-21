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

@Suppress("unused")
val boostFullBackupPatch = bytecodePatch(
    name = "Full Boost backup",
    description = "Adds full backups of local settings, accounts, drafts, and history to Boost's Backup screen.",
) {
    category(PatchCategory.ADVANCED)
    compatibleWith(*AppCompatibility.Boost)
    dependsOn(ExtensionPatches.Boost, keepBoostStoresInOneProcess)

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
