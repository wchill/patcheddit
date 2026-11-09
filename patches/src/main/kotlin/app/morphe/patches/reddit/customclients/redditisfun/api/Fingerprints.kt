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
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

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

private val imgurUriStrings = listOf("https", "api", "imgur", "3", "gallery", "album")

internal object ImgurApiFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Ljava/lang/String;", "Z"),
    strings = imgurUriStrings,
)

internal data class ImgurNativeTargets(
    val uriBuilder: Method,
    val thumbnailLoaders: List<Method>,
    val authorization: String,
)

internal fun resolveImgurTargets(classes: Iterable<ClassDef>): ImgurNativeTargets {
    val methods = classes.flatMap { it.methods }
    fun Method.strings() = implementation?.instructions?.mapNotNull {
        ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
    }.orEmpty().toSet()
    val builders = methods.filter {
        AccessFlags.PRIVATE.isSet(it.accessFlags) && AccessFlags.STATIC.isSet(it.accessFlags)
                && it.returnType == "Landroid/net/Uri;"
                && it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;", "Z")
                && it.strings().containsAll(imgurUriStrings)
    }
    val thumbnails = methods.filter {
        it.returnType == "Ljava/lang/String;"
                && it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/net/Uri;")
                && it.strings().containsAll(listOf(
                    "imgur", "album", "cover", "Authorization", "Could not parse Imgur album JSON",
                ))
    }
    val error = "Unsupported or ambiguous RIF Imgur integration. " +
            "Use an unmodified RIF 5.6.22 free or Golden Platinum APK."
    require(builders.size == 1 && thumbnails.size == 2
            && thumbnails.map { it.definingClass }.distinct().size == 1
            && thumbnails.count { "gallery" in it.strings() } == 1) { error }
    val authorizations = thumbnails.map {
        it.strings().filter { value -> value.startsWith("Client-ID ") }.singleOrNull()
    }
    val authorization = authorizations.filterNotNull().distinct().singleOrNull()
    require(authorizations.none { it == null }
            && authorization != null && authorization.matches(Regex("Client-ID [A-Za-z0-9]+"))) { error }
    return ImgurNativeTargets(builders.single(), thumbnails, authorization)
}
