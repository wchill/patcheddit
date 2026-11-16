/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.infinity.fix.links

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ESCAPED_URLS
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixEscapedUrlsPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef

internal fun isNativeResponseBody(owner: ClassDef): Boolean =
    AccessFlags.ABSTRACT.isSet(owner.accessFlags) && "Ljava/io/Closeable;" in owner.interfaces &&
        owner.methods.any { it.parameterTypes.isEmpty() && it.returnType == "Ljava/io/InputStream;" } &&
        owner.methods.any { it.parameterTypes.isEmpty() && it.returnType == "Ljava/nio/charset/Charset;" } &&
        owner.methods.any {
            it.parameterTypes.isEmpty() && it.returnType == "J" && AccessFlags.ABSTRACT.isSet(it.accessFlags)
        }

@Suppress("unused")
val fixEscapedUrlsPatch = fixEscapedUrlsPatch(
    ExtensionPatches.Infinity, AppCompatibility.Infinity,
) { setting ->
    // Infinity keeps its own obfuscated OkHttp. Repair its decoded API response before Retrofit parses it.
    Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        parameters = emptyList(),
        returnType = "Ljava/lang/String;",
        custom = { method, owner ->
            !owner.type.startsWith("Lapp/morphe/") && isNativeResponseBody(owner) &&
                method.implementation != null && !AccessFlags.STATIC.isSet(method.accessFlags)
        },
    ).wrapMorpheHook(
        setting = setting,
        after = """
            invoke-static {v0}, $ESCAPED_URLS->repairRedditResponse(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v0
        """.trimIndent(),
    )
}
