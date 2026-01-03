package app.morphe.patches.reddit.layout.disablescreenshotpopup

import app.morphe.patcher.Fingerprint

internal val disableScreenshotPopupFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Landroidx/compose/runtime/", "I"),
    custom = { method, classDef ->
        if (!classDef.endsWith("\$ScreenshotTakenBannerKt\$lambda-1\$1;")) {
            return@Fingerprint false
        }

        method.name == "invoke"
    }
)
