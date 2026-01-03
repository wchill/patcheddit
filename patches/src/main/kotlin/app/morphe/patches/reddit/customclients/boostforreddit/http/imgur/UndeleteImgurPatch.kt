package app.morphe.patches.reddit.customclients.boostforreddit.http.imgur

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference


internal const val OKHTTP_EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/boostforreddit/http/OkHttpRequestHook;"

@Suppress("unused")
val interceptImgurRequests = bytecodePatch(
    name="Automatically undelete Imgur images"
) {
    dependsOn(sharedExtensionPatch, interceptHttpRequests)
    compatibleWith("com.rubenmayayo.reddit")

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
                        invoke-static       { v1 }, $OKHTTP_EXTENSION_CLASS_DESCRIPTOR->installInterceptor(Lokhttp3/OkHttpClient${'$'}Builder;)Lokhttp3/OkHttpClient${'$'}Builder;
                        """
                    )
                }
            }
    }
}