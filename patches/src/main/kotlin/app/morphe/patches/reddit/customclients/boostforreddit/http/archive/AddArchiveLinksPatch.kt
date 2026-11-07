/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http.archive

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal val SETTINGS_UTILS_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boost/utils/SettingsUtils;"

@Suppress("unused")
val addArchiveLinks = morpheBytecodePatch(
    name = "Add archive links to context menu",
    description = "Adds 'Open with Wayback Machine' and 'Open with Archive.is' to the link context menu.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.LINKS,
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)
    execute {
        setting.guardHook(SETTINGS_UTILS_EXTENSION_CLASS_DESCRIPTOR, "addMenuOptions")
        LinkBuildContextMenuFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                getReference<MethodReference>()?.toString() ==
                    BoostApi.BoostMenuView.setMenuOptions.toString()
            }
            val listRegister = getInstruction<FiveRegisterInstruction>(index).registerD
            // Several paths branch directly to setMenuOptions, so inject after their convergence point.
            addInstructions(
                index + 1,
                """
                invoke-static {v$listRegister}, $SETTINGS_UTILS_EXTENSION_CLASS_DESCRIPTOR->addMenuOptions(Ljava/util/List;)V
                """
            )
        }

        OnClickContextMenuFingerprint.wrapHook(
            setting = setting,
            before = """
                iget v0, p1, ${BoostApi.MenuOption.id}
                const/16 v1, 101
                if-eq v0, v1, :morphe_wayback
                const/16 v1, 102
                if-eq v0, v1, :morphe_archive
                goto :morphe_native_context_menu
                :morphe_wayback
                const-string v2, "https://web.archive.org/web/"
                goto :morphe_open_archive
                :morphe_archive
                const-string v2, "https://archive.is/"
                :morphe_open_archive
                iget-object v0, p0, ${BoostApi.LinkMenuListener.linkMenu}
                iget-object v1, v0, ${BoostApi.LinkMenu.context}
                iget-object v0, v0, ${BoostApi.LinkMenu.url}
                invoke-static {v1, v0, v2}, $SETTINGS_UTILS_EXTENSION_CLASS_DESCRIPTOR->openArchiveLink(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V
                iget-object v0, p0, ${BoostApi.LinkMenuListener.linkMenu}
                iget-object v0, v0, ${BoostApi.LinkMenu.dialog}
                if-eqz v0, :morphe_archive_menu_closed
                invoke-virtual {v0}, ${BoostApi.MaterialDialog.dismiss}
                :morphe_archive_menu_closed
                return-void
                :morphe_native_context_menu
            """,
        )
    }
}
