/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http.imgur

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.iface.reference.MethodReference


internal const val OKHTTP_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boost/http/OkHttpRequestHook;"

@Suppress("unused")
val interceptImgurRequests = bytecodePatch(
    name = "Automatically undelete Imgur images",
    default = true
) {
    dependsOn(ExtensionPatches.Boost, interceptHttpRequests)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        EnableImgurUndeleteFingerprint.method.returnEarly(true)
    }
}