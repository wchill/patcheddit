/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.ads

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patcher.util.returnEarly

// Based on: https://github.com/ReVanced/revanced-patches/issues/661#issuecomment-2549674017
val fakePremiumPatch = morpheBytecodePatch(
    name = "Fake reddit premium",
    description = "Allows using pro features without ads.",
    default = true,
    extensionPatch = ExtensionPatches.RIF,
) { setting ->
    compatibleWith(AppCompatibility.RedditIsFunFree)

    execute {
        setting.patchMethod(UserPremiumFingerprint) { returnEarly(true) }
    }
}