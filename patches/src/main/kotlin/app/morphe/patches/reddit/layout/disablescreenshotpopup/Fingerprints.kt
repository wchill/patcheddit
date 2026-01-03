package app.morphe.patches.reddit.layout.disablescreenshotpopup

import app.morphe.patcher.fingerprint

internal val disableScreenshotPopupFingerprint = fingerprint {
    returns("V")
    parameters("Landroidx/compose/runtime/", "I")
    custom { method, classDef ->
        if (!classDef.endsWith("\$ScreenshotTakenBannerKt\$lambda-1\$1;")) {
            return@custom false
        }

        method.name == "invoke"
    }
}
