/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

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
    custom = { _, classDef ->
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

internal val jrawAuthenticationMethodCheckFingerprint = Fingerprint(
    strings = listOf("This method is not appropriate for this authentication method")
)

internal val oauthHelperConstructorFingerprint = Fingerprint(
    classFingerprint = jrawAuthenticationMethodCheckFingerprint,
    accessFlags = listOf(AccessFlags.CONSTRUCTOR, AccessFlags.PUBLIC),
    filters = listOf(OpcodeFilter(Opcode.INVOKE_STATIC))
)

internal val oauthShouldOverrideUrlLoadingFingerprint = Fingerprint(
    name = "shouldOverrideUrlLoading",
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IF_EQZ,
        Opcode.INVOKE_DIRECT
    ),
    custom = { _, classDef ->
        classDef.sourceFile == "LoginActivity.java"
    }
)

internal val oauthContainsCodeFingerprint = Fingerprint(
    strings = listOf("code="),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.MOVE_RESULT,
        Opcode.RETURN
    ),
    custom = { _, classDef ->
        classDef.sourceFile == "LoginActivity.java"
    }
)
