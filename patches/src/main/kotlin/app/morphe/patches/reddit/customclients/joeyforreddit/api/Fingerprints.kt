package app.morphe.patches.reddit.customclients.joeyforreddit.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal val authUtilityUserAgentFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    filters = listOf(OpcodeFilter(Opcode.APUT_OBJECT)),
    custom = { method, classDef ->
        classDef.sourceFile == "AuthUtility.java"
    }
)

internal val getClientIdFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "L",
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.CONST,               // R.string.valuable_cid
        Opcode.INVOKE_STATIC,       // StringMaster.decrypt
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.RETURN_OBJECT
    ),
    custom = { _, classDef ->
        classDef.sourceFile == "AuthUtility.java"
    }
)
