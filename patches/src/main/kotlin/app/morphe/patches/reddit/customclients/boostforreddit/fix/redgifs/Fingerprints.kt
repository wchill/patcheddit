package app.morphe.patches.reddit.customclients.boostforreddit.fix.redgifs

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal val createOkHttpClientFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.NEW_INSTANCE,
        Opcode.INVOKE_DIRECT,
        Opcode.NEW_INSTANCE,
        Opcode.INVOKE_DIRECT,
        Opcode.NEW_INSTANCE,
        Opcode.INVOKE_DIRECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT
    ),
    custom = { _, classDef -> classDef.sourceFile == "RedGifsAPIv2.java" }
)
