/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.threadcolors

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22t
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private val indentViewOnDrawFingerprint = BoostApi.IndentView.onDraw.fingerprint()

@Suppress("unused")
val repeatThreadLevelColorsPatch = morpheBytecodePatch(
    name = "Repeat Boost thread level colors",
    description = "Repeats the thread level color pattern in deep comment chains for colored lines and bars.",
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.COMMENTS,
) { setting ->
    compatibleWith(*AppCompatibility.Boost)

    execute {
        setting.patchMethod(indentViewOnDrawFingerprint) {
            repeatThreadLevelColors()
        }
    }
}

internal fun MutableMethod.repeatThreadLevelColors() {
    val lengthIndex = indexOfFirstInstructionOrThrow { opcode == Opcode.ARRAY_LENGTH }
    val length = getInstruction<TwoRegisterInstruction>(lengthIndex)
    val bounds = getInstruction<BuilderInstruction22t>(lengthIndex + 1)
    val lookupIndex = lengthIndex + 2
    val lookup = getInstruction<ThreeRegisterInstruction>(lookupIndex)
    check(bounds.opcode == Opcode.IF_GE && bounds.registerB == length.registerA &&
        lookup.opcode == Opcode.AGET && lookup.registerB == length.registerB &&
        lookup.registerC == bounds.registerA
    ) { "Unexpected Boost thread color lookup" }

    val size = length.registerA
    val colors = length.registerB
    val level = lookup.registerC
    replaceInstruction(lengthIndex + 1, BuilderInstruction21t(Opcode.IF_LEZ, size, bounds.target))
    replaceInstruction(lookupIndex, "aget v${lookup.registerA}, v$colors, v$size")
    // Index zero is the root's transparent entry, not part of the repeating palette.
    addInstructionsWithLabels(
        lookupIndex,
        """
            rem-int v$size, v$level, v$size
            if-nez v$size, :morphe_thread_color
            array-length v$size, v$colors
            add-int/lit8 v$size, v$size, -0x1
        """,
        ExternalLabel("morphe_thread_color", getInstruction(lookupIndex)),
    )
    addInstruction(lengthIndex + 1, "add-int/lit8 v$size, v$size, -0x1")
}
