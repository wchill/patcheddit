/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.redgifs

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patches.reddit.customclients.CREATE_NEW_CLIENT_METHOD
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.fixRedgifsApiPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boostforreddit/FixRedgifsApiPatch;"

@Suppress("unused")
val fixRedgifsApi = fixRedgifsApiPatch(
    extensionPatch = sharedExtensionPatch
) {
    dependsOn(interceptHttpRequests, sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        // region Patch Redgifs OkHttp3 client.

        createOkHttpClientFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                val reference = getReference<MethodReference>()
                reference?.name == "build" && reference.definingClass == "Lokhttp3/OkHttpClient\$Builder;"
            }
            replaceInstruction(
                index,
                "invoke-static { }, $EXTENSION_CLASS_DESCRIPTOR->$CREATE_NEW_CLIENT_METHOD"
            )
        }

        // endregion
    }
}
