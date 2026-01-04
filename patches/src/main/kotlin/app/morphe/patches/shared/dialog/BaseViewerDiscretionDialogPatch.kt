package app.morphe.patches.shared.dialog

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.fingerprint.matchOrThrow
import app.morphe.util.fingerprint.methodOrThrow
import app.morphe.util.getReference
import app.morphe.util.getWalkerMethod
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

fun baseViewerDiscretionDialogPatch(
    classDescriptor: String,
    isAgeVerified: Boolean = false
) = bytecodePatch(
    description = "baseViewerDiscretionDialogPatch"
) {
    execute {
        createDialogFingerprint
            .methodOrThrow()
            .invoke(classDescriptor, "confirmDialog")

        if (isAgeVerified) {
            ageVerifiedFingerprint.matchOrThrow().let {
                it.getWalkerMethod(it.instructionMatches.last().index - 1)
                    .invoke(classDescriptor, "confirmDialogAgeVerified")
            }
        }
    }
}

private fun MutableMethod.invoke(classDescriptor: String, methodName: String) {
    val showDialogIndex = indexOfFirstInstructionOrThrow {
        getReference<MethodReference>()?.name == "show"
    }
    val dialogRegister = getInstruction<FiveRegisterInstruction>(showDialogIndex).registerC

    addInstruction(
        showDialogIndex + 1,
        "invoke-static { v$dialogRegister }, $classDescriptor->$methodName(Landroid/app/AlertDialog;)V"
    )
}

