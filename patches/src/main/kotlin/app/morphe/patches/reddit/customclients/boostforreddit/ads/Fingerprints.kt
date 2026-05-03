package app.morphe.patches.reddit.customclients.boostforreddit.ads

import app.morphe.patcher.Fingerprint

internal object MaxMediationFingerprint : Fingerprint(
    strings = listOf("MaxMediation: Attempting to initialize SDK")
)

internal object AdmobMediationFingerprint : Fingerprint(
    strings = listOf("AdmobMediation: Attempting to initialize SDK")
)
