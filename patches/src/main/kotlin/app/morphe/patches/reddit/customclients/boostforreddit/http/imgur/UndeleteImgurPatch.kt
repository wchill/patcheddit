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
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
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
val interceptImgurRequests = morpheBytecodePatch(
    name = "Automatically undelete Imgur images",
    description = "Loads deleted Imgur images from the Wayback Machine.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.UNDELETE,
    preferenceKey = "morphe_boost_imgur_undelete_enabled",
    defaultEnabled = false,
    settingsSummary = "Restore missing Imgur images and albums from the Wayback Machine. " +
        "Links to missing Imgur media you open are sent to this third-party archive. Off by default.",
) {
    dependsOn(ExtensionPatches.Boost, interceptHttpRequests, undeleteAppliedMarkerPatch(IMGUR_UNDELETE_APPLIED_RESOURCE))
    compatibleWith(*AppCompatibility.Boost)

    execute {
        EnableImgurUndeleteFingerprint.method.returnEarly(true)
    }
}
