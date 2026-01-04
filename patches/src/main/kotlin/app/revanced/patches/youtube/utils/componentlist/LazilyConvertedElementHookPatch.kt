package app.revanced.patches.youtube.utils.componentlist

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patches.shared.conversionContextFingerprintToString
import app.revanced.patches.shared.litho.componentContextSubParserFingerprint
import app.revanced.patches.youtube.utils.extension.Constants.UTILS_PATH
import app.revanced.patches.youtube.utils.extension.sharedExtensionPatch
import app.revanced.util.addInstructionsAtControlFlowLabel
import app.revanced.util.findFreeRegister
import app.revanced.util.fingerprint.methodOrThrow
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import app.revanced.util.indexOfFirstInstructionReversedOrThrow
import app.revanced.util.indexOfFirstStringInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "$UTILS_PATH/LazilyConvertedElementPatch;"

private lateinit var lazilyConvertedElementMethod: MutableMethod

val lazilyConvertedElementHookPatch = bytecodePatch(
    description = "lazilyConvertedElementHookPatch"
) {
    dependsOn(sharedExtensionPatch)

    execute {
        componentListFingerprint.methodOrThrow(componentContextSubParserFingerprint).apply {
            val identifierReference = with(conversionContextFingerprintToString.methodOrThrow()) {
                val identifierStringIndex =
                    indexOfFirstStringInstructionOrThrow(", identifierProperty=")
                val identifierStringAppendIndex =
                    indexOfFirstInstructionOrThrow(identifierStringIndex, Opcode.INVOKE_VIRTUAL)
                val identifierAppendIndex =
                    indexOfFirstInstructionOrThrow(
                        identifierStringAppendIndex + 1,
                        Opcode.INVOKE_VIRTUAL
                    )
                val identifierRegister =
                    getInstruction<FiveRegisterInstruction>(identifierAppendIndex).registerD
                val identifierIndex =
                    indexOfFirstInstructionReversedOrThrow(identifierAppendIndex) {
                        opcode == Opcode.IGET_OBJECT &&
                                getReference<FieldReference>()?.type == "Ljava/lang/String;" &&
                                (this as? TwoRegisterInstruction)?.registerA == identifierRegister
                    }
                getInstruction<ReferenceInstruction>(identifierIndex).reference
            }

            val listIndex = implementation!!.instructions.lastIndex
            val listRegister = getInstruction<OneRegisterInstruction>(listIndex).registerA
            val identifierRegister = findFreeRegister(listIndex, listRegister)

            addInstructionsAtControlFlowLabel(
                listIndex, """
                    move-object/from16 v$identifierRegister, p2
                    iget-object v$identifierRegister, v$identifierRegister, $identifierReference
                    invoke-static {v$listRegister, v$identifierRegister}, $EXTENSION_CLASS_DESCRIPTOR->hookElements(Ljava/util/List;Ljava/lang/String;)V
                    """
            )

            lazilyConvertedElementMethod = lazilyConvertedElementPatchFingerprint.methodOrThrow()
        }
    }
}

internal fun hookElementList(descriptor: String) =
    lazilyConvertedElementMethod.addInstruction(
        0,
        "invoke-static {p0, p1}, $descriptor(Ljava/util/List;Ljava/lang/String;)V"
    )
