/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed

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
    }
}
