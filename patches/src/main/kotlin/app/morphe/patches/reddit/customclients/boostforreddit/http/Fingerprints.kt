package app.morphe.patches.reddit.customclients.boostforreddit.http

import app.morphe.patcher.fingerprint

internal val installOkHttpInterceptorFingerprint = fingerprint {
    custom { method, _ -> method.name == "c" && method.definingClass == "Ltb/a;" }
}