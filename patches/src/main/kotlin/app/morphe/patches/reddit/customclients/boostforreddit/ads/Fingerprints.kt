package app.morphe.patches.reddit.customclients.boostforreddit.ads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

internal val maxMediationFingerprint = Fingerprint(
    filters = listOf(string("MaxMediation: Attempting to initialize SDK"))
)

internal val admobMediationFingerprint = Fingerprint(
    filters = listOf(string("AdmobMediation: Attempting to initialize SDK"))
)
