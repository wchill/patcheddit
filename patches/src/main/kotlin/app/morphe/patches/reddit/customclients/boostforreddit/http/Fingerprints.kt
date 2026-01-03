package app.morphe.patches.reddit.customclients.boostforreddit.http

import app.morphe.patcher.Fingerprint

internal val installOkHttpInterceptorFingerprint = Fingerprint(
    custom = { method, _ -> method.name == "c" && method.definingClass == "Ltb/a;" }
)
