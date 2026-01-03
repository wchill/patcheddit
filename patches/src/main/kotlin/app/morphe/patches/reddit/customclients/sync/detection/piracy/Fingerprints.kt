package app.morphe.patches.reddit.customclients.sync.detection.piracy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.Reference

internal val piracyDetectionFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    filters = OpcodesFilter.opcodesToFilters (
        Opcode.NEW_INSTANCE,
        Opcode.INVOKE_DIRECT,
        Opcode.NEW_INSTANCE,
        Opcode.INVOKE_DIRECT,
        Opcode.INVOKE_VIRTUAL,
    ),
    custom = { method, _ ->
        method.implementation ?: return@Fingerprint false
        method.instructions.any {
            it.getReference<Reference>()?.toString() == "Lcom/github/javiersantos/piracychecker/PiracyChecker;"
        }
    }
)
