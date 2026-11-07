/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.profiletabs

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import app.morphe.patches.all.misc.PatchCategory

private const val EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boost/RemoveObsoleteProfileTabsPatch;"

@Suppress("unused")
val removeObsoleteProfileTabsPatch = morpheBytecodePatch(
    name = "Remove obsolete profile tabs",
    description = "Removes the legacy Gilded and Friends tabs from Boost profiles.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.INTERFACE,
) { setting ->
    compatibleWith(*AppCompatibility.Boost)
    dependsOn(ExtensionPatches.Boost)

    execute {
        setting.guardHook(EXTENSION_DESCRIPTOR, "removeObsoleteTabs")
        userActivityBuildTabsFingerprint.classDef.methods.single { candidate ->
            candidate.parameters.map { it.type } == listOf("Landroid/os/Bundle;") &&
                candidate.implementation?.instructions?.any { instruction ->
                    instruction.opcode == Opcode.IPUT_OBJECT &&
                        (instruction as? ReferenceInstruction)?.reference?.toString() ==
                        BoostApi.UserActivity.tabRoutes.toString()
                } == true
        }.apply {
            val routeAssignmentIndices =
                implementation!!.instructions
                    .withIndex()
                    .filter { (_, instruction) ->
                        instruction.opcode == Opcode.IPUT_OBJECT &&
                            (instruction as? ReferenceInstruction)
                                ?.reference
                                ?.toString() ==
                            BoostApi.UserActivity.tabRoutes.toString()
                    }
                    .map { (index, _) -> index }

            require(routeAssignmentIndices.size == 2) {
                "UserActivity.onCreate expected two route-array assignments, found ${routeAssignmentIndices.size}"
            }

            routeAssignmentIndices.asReversed().forEach { assignmentIndex ->
                val activityRegister = getInstruction<TwoRegisterInstruction>(assignmentIndex).registerB
                addInstructions(
                    assignmentIndex + 1,
                    """
                        invoke-static {v$activityRegister}, $EXTENSION_DESCRIPTOR->removeObsoleteTabs(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }
        }
    }
}
