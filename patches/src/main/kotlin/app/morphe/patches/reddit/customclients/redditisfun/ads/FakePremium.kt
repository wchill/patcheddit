/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.ads

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.redditisfun.RedditIsFunCompatible
import app.morphe.patches.reddit.customclients.redditisfun.RedditIsFunFreeCompatible
import app.morphe.util.returnEarly

// Based on: https://github.com/ReVanced/revanced-patches/issues/661#issuecomment-2549674017
val fakePremiumPatch = bytecodePatch(
    name = "Fake reddit premium",
    description = "Allows using pro features without ads.",
    default = true
) {
    compatibleWith(*RedditIsFunCompatible)

    execute {
        userPremiumFingerprint.method.returnEarly(true)
    }
}