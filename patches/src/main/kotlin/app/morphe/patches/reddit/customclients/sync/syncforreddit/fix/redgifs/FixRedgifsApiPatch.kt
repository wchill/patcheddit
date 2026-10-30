/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.redgifs

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patches.reddit.customclients.INSTALL_NEW_CLIENT_METHOD
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixRedgifsApiPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/syncforreddit/FixRedgifsApiPatch;"

@Suppress("unused")
val fixRedgifsApi = fixRedgifsApiPatch(
    extensionPatch = ExtensionPatches.Sync
) {
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        // region Patch Redgifs OkHttp3 client.

        CreateOkHttpClientFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                val reference = getReference<MethodReference>()
                reference?.name == "build" && reference.definingClass == "Lokhttp3/OkHttpClient\$Builder;"
            }
            val register = getInstruction<FiveRegisterInstruction>(index).registerC
            replaceInstruction(
                index,
                """
                invoke-static       { v$register }, $EXTENSION_CLASS_DESCRIPTOR->$INSTALL_NEW_CLIENT_METHOD
                """
            )
        }

        GetDefaultUserAgentFingerprint.method.apply {
            addInstructions(
                0,
                """
                invoke-static { }, ${GetOriginalUserAgentFingerprint.method}
                move-result-object v0
                return-object v0
                """
            )
        }

        // endregion

        // region Fix gif id extraction.

        // getGfycatId strips the trailing slash before the query, so links like
        // "redgifs.com/watch/<id>/?92/" yield an empty id and the API call 404s.
        GetGfycatIdFingerprint.method.addInstructions(
            0,
            """
                invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->extractGifId(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p0
                return-object p0
            """
        )

        // endregion
    }
}
