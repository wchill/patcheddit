package app.morphe.patches.reddit.customclients.redditisfun.ads

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.returnEarly

// Based on: https://github.com/ReVanced/revanced-patches/issues/661#issuecomment-2549674017
val fakePremiumPatch = bytecodePatch(
    name = "Fake reddit premium",
    description = "Allows using pro features without ads."
) {
    compatibleWith(
        "com.andrewshu.android.reddit"("5.6.22"),
    )

    execute {
        userPremiumFingerprint.method.returnEarly(true)
    }
}