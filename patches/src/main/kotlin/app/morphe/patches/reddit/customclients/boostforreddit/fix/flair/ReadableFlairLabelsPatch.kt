/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.flair

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import app.morphe.patches.all.misc.PatchCategory

private const val FLAIR_CONTRAST_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boost/utils/BoostFlairContrast;"

private val flairColorsFingerprint = BoostApi.RichFlairView.flairColors.fingerprint()

@Suppress("unused")
val readableFlairLabelsPatch = morpheBytecodePatch(
    name = "Readable Boost flair labels",
    description =
        "Uses black or white flair text when Reddit's flair text color is unreadable on its background.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.FEED,
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        setting.guardHook(FLAIR_CONTRAST_EXTENSION_DESCRIPTOR, "readableTextColor", identity = true)
        flairColorsFingerprint.method.apply {
            val backgroundIndex = indexOfFirstInstructionOrThrow {
                getReference<MethodReference>()?.toString() ==
                    "Landroid/graphics/drawable/GradientDrawable;->setColor(I)V"
            }
            val textIndex = indexOfFirstInstructionOrThrow {
                getReference<MethodReference>()?.toString() == BoostApi.RichFlairView.setTextColor.toString()
            }
            check(backgroundIndex < textIndex)
            val backgroundRegister = getInstruction<FiveRegisterInstruction>(backgroundIndex).registerD
            val textRegister = getInstruction<FiveRegisterInstruction>(textIndex).registerD
            addInstructions(
                textIndex,
                """
                    invoke-static {v$textRegister, v$backgroundRegister}, $FLAIR_CONTRAST_EXTENSION_DESCRIPTOR->readableTextColor(II)I
                    move-result v$textRegister
                """,
            )
        }
    }
}
