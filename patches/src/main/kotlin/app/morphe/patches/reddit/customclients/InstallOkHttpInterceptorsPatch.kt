/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed

@Suppress("unused")
internal fun interceptHttpRequestsPatch(
    dependsOn: Array<Patch<*>>,
    compatibleWith: Array<Compatibility>,
    extensionClassDescriptor: String
) = bytecodePatch(
    default = false
) {
    dependsOn(*dependsOn)
    compatibleWith(*compatibleWith)

    execute {
        Fingerprint(
            filters = listOf(
                methodCall(
                    definingClass = $$"Lokhttp3/OkHttpClient$Builder;",
                    name = "build",
                )
            ),
            custom = { _, classDef ->
                !classDef.type.startsWith("Lapp/morphe/")
            }
        ).matchAll().forEach { match ->
            val index = match.instructionMatches[0].index
            val register = match.instructionMatches[0].instruction.registersUsed[0]
            match.method.addInstructions(
                index,
                $$"""
                invoke-static       { }, $$extensionClassDescriptor->init()V
                invoke-static       { v$$register }, $$extensionClassDescriptor->installInterceptor(Lokhttp3/OkHttpClient$Builder;)Lokhttp3/OkHttpClient$Builder;
                move-result-object  v$$register
                """
            )
        }
    }
}