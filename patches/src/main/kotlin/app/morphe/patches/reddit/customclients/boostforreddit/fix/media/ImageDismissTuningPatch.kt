/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.media

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import app.morphe.patches.all.misc.PatchCategory

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/boost/media/ImageDismissTuning;"

private const val CONTRACT_MARKER =
    "MORPHE_BOOST_IMAGE_DISMISS_TUNING_ISSUE96_V4_1"

private val mediaImageActivityOnCreateFingerprint = BoostApi.MediaImageActivity.onCreate.fingerprint()

private val legacyImageActivityOnCreateFingerprint = BoostApi.ImageActivity.onCreate.fingerprint()

private val legacyImageActivity2OnCreateFingerprint = BoostApi.ImageActivity2.onCreate.fingerprint()

private val imageModelFragmentOnCreateViewFingerprint = BoostApi.ImageModelFragment.onCreateView.fingerprint()

@Suppress("unused")
val imageDismissTuningPatch = morpheBytecodePatch(
    name = "Tune Boost image dismissal",
    description =
        "Lets you close the image viewer with a quick flick, without changing GIF, video or other gestures.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.MEDIA,
    optionsResource = "morphe_boost_image_dismiss_options",
    resourceDirectory = "boost",
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        check(CONTRACT_MARKER.endsWith("ISSUE96_V4_1"))

        arrayOf(
            mediaImageActivityOnCreateFingerprint,
            legacyImageActivityOnCreateFingerprint,
            legacyImageActivity2OnCreateFingerprint,
        ).forEach { fingerprint ->
            fingerprint.wrapHook(
                setting = setting,
                after = "invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->apply(Ljava/lang/Object;)V",
            )
        }

        imageModelFragmentOnCreateViewFingerprint.wrapHook(
            setting = setting,
            after = "invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->apply(Ljava/lang/Object;)V",
        )
    }
}
