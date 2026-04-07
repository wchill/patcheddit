/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http.imgur

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference


internal const val OKHTTP_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boostforreddit/http/OkHttpRequestHook;"

@Suppress("unused")
val interceptImgurRequests = bytecodePatch(
    name = "Automatically undelete Imgur images",
    default = true
) {
    dependsOn(sharedExtensionPatch, interceptHttpRequests)
    compatibleWith(*BoostCompatible)

    execute {
        arrayOf(installImgurFreeOkHttpInterceptorFingerprint, installImgurPaidOkHttpInterceptorFingerprint)
            .forEach {
                it.method.apply {
                    val index = indexOfFirstInstruction {
                        val reference = getReference<MethodReference>() ?: return@indexOfFirstInstruction false
                        reference.toString() == "Lokhttp3/OkHttpClient${'$'}Builder;-><init>()V"
                    }
                    addInstructions(
                        index + 1,
                        """
                        invoke-static       { }, $OKHTTP_EXTENSION_CLASS_DESCRIPTOR->init()V
                        invoke-static       { v1 }, $OKHTTP_EXTENSION_CLASS_DESCRIPTOR->installInterceptor(Lokhttp3/OkHttpClient${'$'}Builder;)Lokhttp3/OkHttpClient${'$'}Builder;
                        """
                    )
                }
            }
    }
}