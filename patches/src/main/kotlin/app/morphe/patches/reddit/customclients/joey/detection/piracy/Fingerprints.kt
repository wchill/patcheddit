package app.morphe.patches.reddit.customclients.joey.detection.piracy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object PiracyDetectionFingerprint : Fingerprint(
    definingClass = "/ProcessLifeCyleListener;",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    returnType = "V",
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.NEW_INSTANCE,
        Opcode.CONST_16,
        Opcode.CONST_WIDE_16,
        Opcode.INVOKE_DIRECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.RETURN_VOID
    ),
)
