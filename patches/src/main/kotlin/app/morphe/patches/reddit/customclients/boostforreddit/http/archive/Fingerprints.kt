package app.morphe.patches.reddit.customclients.boostforreddit.http.archive

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.Opcode

internal val linkBuildContextMenuFingerprint = Fingerprint(
    // Lcom/rubenmayayo/reddit/ui/customviews/t;->f(Ljava/lang/String;)V
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.INVOKE_INTERFACE,
        Opcode.NEW_INSTANCE,
        Opcode.INVOKE_DIRECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.CONST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.CONST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.INVOKE_INTERFACE,
        Opcode.NEW_INSTANCE
    ),
    custom = {
        method, _ -> method.name == "f" && method.definingClass == "Lcom/rubenmayayo/reddit/ui/customviews/t;"
    }
)

internal val onClickContextMenuFingerprint = Fingerprint(
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.MOVE_RESULT,
        Opcode.IF_EQZ
    ),
    custom = {
        method, _ -> method.name == "c" && method.definingClass == "Lcom/rubenmayayo/reddit/ui/customviews/t;"
    }
)
