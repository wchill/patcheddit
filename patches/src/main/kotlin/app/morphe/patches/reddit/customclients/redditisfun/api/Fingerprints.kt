/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object BasicAuthorizationFingerprint : Fingerprint(
    strings = listOf("yyOCBp.RHJhDKd", "fJOxVwBUyo*=f:<OoejWs:AqmIJ") // Encrypted basic authorization string.
)

internal object BuildAuthorizationStringFingerprint : Fingerprint(
    strings = listOf("yyOCBp.RHJhDKd", "client_id")
)

internal object GetUserAgentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.NEW_ARRAY,
        Opcode.CONST_4,
        Opcode.INVOKE_STATIC,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.APUT_OBJECT,
        Opcode.CONST,
    )
)

internal object RedirectUriFingerprint : Fingerprint(
    strings = listOf("redditisfun://auth"),
)

// Should usually match: g2/c.x()
internal object ImgurApiFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Ljava/lang/String;", "Z"),
    strings = listOf("https", "api", "imgur", "3", "gallery", "album")
)
