/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

fun modifyWebViewPatch(
    extensionPatch: Array<Patch<*>>,
    compatible: Array<Compatibility>
) = bytecodePatch(
    name = "Modify login WebView",
    description = "Modify the WebView used for logging into reddit to prevent login issues",
    default = true
) {
    compatibleWith(*compatible)

    dependsOn(
        *extensionPatch,
    )

    execute {
        Fingerprint(
            filters = listOf(
                methodCall(
                    definingClass = "Landroid/webkit/WebView;",
                    name = "loadUrl"
                )
            ),
            custom = { _, classDef ->
                !classDef.type.startsWith("Lapp/morphe/")
            }
        ).matchAll().forEach { match ->
            println("${match.classDef}->${match.method.name}")
            val index = match.instructionMatches[0].index
            val instruction = match.instructionMatches[0].instruction
            val webviewReg = instruction.registersUsed[0]
            val urlReg = instruction.registersUsed[1]
            if (instruction.registersUsed.size == 2) {
                match.method.replaceInstruction(
                    index,
                    """
                        invoke-static { v$webviewReg, v$urlReg }, Lapp/morphe/extension/shared/fixes/login/ModifyWebViewPatch;->loadUrl(Landroid/webkit/WebView;Ljava/lang/String;)V
                    """
                )
            } else {
                val headersReg = instruction.registersUsed[2]
                match.method.replaceInstruction(
                    index,
                    """
                        invoke-static { v$webviewReg, v$urlReg, v$headersReg }, Lapp/morphe/extension/shared/fixes/login/ModifyWebViewPatch;->loadUrl(Landroid/webkit/WebView;Ljava/lang/String;Ljava/util/Map;)V
                    """
                )
            }
        }

        classDefForEach { classDef ->
            if (classDef.superclass != "Landroid/webkit/WebViewClient;") return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)
            val method = mutableClass.virtualMethods.firstOrNull { it.name == "onPageFinished" } ?: ImmutableMethod(
                classDef.type,
                "onPageFinished",
                listOf(
                    ImmutableMethodParameter("Landroid/webkit/WebView;", emptySet(), "view"),
                    ImmutableMethodParameter("Ljava/lang/String;", emptySet(), "url")
                ),
                "V",
                AccessFlags.PUBLIC.value,
                null,
                null,
                MutableMethodImplementation(3),
            ).toMutable().also {
                it.addInstructions(
                    0,
                    """
                        return-void
                    """
                )
                mutableClass.virtualMethods.add(it)
            }

            method.let { method ->
                val thisReg = method.p0Register
                val webViewReg = thisReg + 1
                val urlReg = thisReg + 2
                method.addInstructions(
                    0,
                    """
                        invoke-static { v$webViewReg, v$urlReg }, Lapp/morphe/extension/shared/fixes/login/ModifyWebViewPatch;->onPageFinished(Landroid/webkit/WebView;Ljava/lang/String;)V
                    """
                )
            }
        }
    }
}
