/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http.imgur

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.patches.reddit.customclients.boostforreddit.http.IMGUR_UNDELETE_APPLIED_RESOURCE
import app.morphe.patches.reddit.customclients.boostforreddit.http.undeleteAppliedMarkerPatch
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import app.morphe.patches.all.misc.PatchCategory

internal const val OKHTTP_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boost/http/OkHttpRequestHook;"

@Suppress("unused")
val interceptImgurRequests = bytecodePatch(
    name = "Automatically undelete Imgur images",
    description = "Loads deleted Imgur images from the Wayback Machine.",
    default = true
) {
    category(PatchCategory.UNDELETE)
    dependsOn(ExtensionPatches.Boost, interceptHttpRequests, undeleteAppliedMarkerPatch(IMGUR_UNDELETE_APPLIED_RESOURCE))
    compatibleWith(*AppCompatibility.Boost)

    execute {
        EnableImgurUndeleteFingerprint.method.returnEarly(true)
    }
}
