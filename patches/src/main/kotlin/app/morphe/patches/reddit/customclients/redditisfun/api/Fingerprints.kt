package app.morphe.patches.reddit.customclients.redditisfun.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal fun baseClientIdFingerprint(str: String) = Fingerprint(
    strings = listOf("yyOCBp.RHJhDKd", str)
)


internal val basicAuthorizationFingerprint = baseClientIdFingerprint(
    str = "fJOxVwBUyo*=f:<OoejWs:AqmIJ", // Encrypted basic authorization string.
)

internal val buildAuthorizationStringFingerprint = baseClientIdFingerprint(
    str = "client_id",
)

internal val getUserAgentFingerprint = Fingerprint(
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

// TODO: These obfuscated names are bad, but the app is abandoned so these will not be changing.
// This can be fixed later once patcher API is capable of matching multiple methods with one fingerprint.
internal val oAuth2ActivityD0Fingerprint = Fingerprint(
    strings = listOf("redditisfun://auth"),
    custom = { method, classDef ->
        if (!classDef.endsWith("OAuth2Activity${'$'}b;")) return@Fingerprint false

        method.name == "d0"
    }
)

internal val oAuth2ActivityShouldOverrideUrlLoadingFingerprint = Fingerprint(
    strings = listOf("redditisfun://auth"),
    custom = { method, classDef ->
        if (!classDef.endsWith("OAuth2Activity${'$'}a;")) return@Fingerprint false

        method.name == "shouldOverrideUrlLoading"
    }
)

internal val cActivityJFingerprint = Fingerprint(
    strings = listOf("redditisfun://auth"),
    custom = { method, classDef ->
        if (!classDef.endsWith("c;")) return@Fingerprint false

        method.name == "j"
    }
)

// Should usually match: g2/c.x()
internal val imgurApiFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Ljava/lang/String;", "Z"),
    strings = listOf("https", "api", "imgur", "3", "gallery", "album")
)
