/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.joey.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object AuthUtilityUserAgentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    filters = listOf(OpcodeFilter(Opcode.APUT_OBJECT)),
    custom = { _, classDef ->
        classDef.sourceFile == "AuthUtility.java"
    }
)

internal object GetClientIdFingerprint : Fingerprint(
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

internal object JrawAuthenticationMethodCheckFingerprint : Fingerprint(
    strings = listOf("This method is not appropriate for this authentication method")
)

internal object OauthHelperConstructorFingerprint : Fingerprint(
    classFingerprint = JrawAuthenticationMethodCheckFingerprint,
    accessFlags = listOf(AccessFlags.CONSTRUCTOR, AccessFlags.PUBLIC),
    filters = listOf(OpcodeFilter(Opcode.INVOKE_STATIC))
)

internal object OauthShouldOverrideUrlLoadingFingerprint : Fingerprint(
    name = "shouldOverrideUrlLoading",
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IF_EQZ,
        Opcode.INVOKE_DIRECT
    ),
    custom = { _, classDef ->
        classDef.sourceFile == "LoginActivity.java"
    }
)

internal object OauthContainsCodeFingerprint : Fingerprint(
    strings = listOf("code="),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.MOVE_RESULT,
        Opcode.RETURN
    ),
    custom = { _, classDef ->
        classDef.sourceFile == "LoginActivity.java"
    }
)
