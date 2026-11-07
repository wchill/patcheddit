/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.joey.misc.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.MORPHE_SETTINGS_DESCRIPTION
import app.morphe.patches.reddit.customclients.MORPHE_SETTINGS_HOOK
import app.morphe.patches.reddit.customclients.addMorpheLayoutEntry
import app.morphe.patches.reddit.customclients.morpheSettingsResourcesPatch
import app.morphe.patches.reddit.customclients.morpheSettingsContextPatch
import app.morphe.patches.reddit.customclients.settingsResourcePaths
import com.android.tools.smali.dexlib2.Opcode

private val joeySettingsResourcesPatch = resourcePatch(default = false) {
    dependsOn(morpheSettingsResourcesPatch)
    execute {
        for (path in settingsResourcePaths("layout", "settings_activity")) {
            document(path).use(::addMorpheLayoutEntry)
        }
    }
}

@Suppress("unused")
val joeyMorpheSettingsPatch = bytecodePatch(
    name = "Joey Morphe settings",
    description = MORPHE_SETTINGS_DESCRIPTION,
    default = true,
) {
    category(PatchCategory.INTERFACE)
    compatibleWith(*AppCompatibility.JoeyForReddit)
    dependsOn(ExtensionPatches.Joey, joeySettingsResourcesPatch, morpheSettingsContextPatch)

    execute {
        Fingerprint(
            definingClass = "Lo/o/joey/SettingActivities/SettingsActivity;",
            name = "onCreate", parameters = listOf("Landroid/os/Bundle;"), returnType = "V",
        ).method.apply {
            val returns = checkNotNull(implementation).instructions.mapIndexedNotNull { index, instruction ->
                index.takeIf { instruction.opcode == Opcode.RETURN_VOID }
            }
            require(returns.isNotEmpty()) { "Joey settings have no lifecycle return." }
            for (index in returns.reversed()) {
                addInstruction(index,
                    "invoke-static/range {p0 .. p0}, $MORPHE_SETTINGS_HOOK->bindEntry(Landroid/app/Activity;)V")
            }
        }
    }
}
