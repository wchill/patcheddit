/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.indexOfFirstInstructionReversed
import com.android.tools.smali.dexlib2.Opcode


internal const val OKHTTP_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boostforreddit/http/OkHttpRequestHook;"

@Suppress("unused")
val interceptHttpRequests = bytecodePatch(
    default = false
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        installOkHttpInterceptorFingerprint.method.apply {
            val index = indexOfFirstInstructionReversed(Opcode.INVOKE_VIRTUAL)
            addInstructions(
                index,
                """
                invoke-static       { p0 }, $OKHTTP_EXTENSION_CLASS_DESCRIPTOR->installInterceptor(Lokhttp3/OkHttpClient${'$'}Builder;)Lokhttp3/OkHttpClient${'$'}Builder;
                move-result-object  p0
                """
            )
        }
    }
}