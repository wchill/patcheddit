package app.morphe.patches.reddit.customclients.boostforreddit.ads

import app.morphe.patcher.Fingerprint

internal val maxMediationFingerprint = Fingerprint(
    strings = listOf("MaxMediation: Attempting to initialize SDK")
)

internal val admobMediationFingerprint = Fingerprint(
    strings = listOf("AdmobMediation: Attempting to initialize SDK")
)
