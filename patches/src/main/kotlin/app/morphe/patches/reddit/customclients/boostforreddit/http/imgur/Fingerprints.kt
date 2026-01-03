package app.morphe.patches.reddit.customclients.boostforreddit.http.imgur

import app.morphe.patcher.fingerprint


internal val installImgurPaidOkHttpInterceptorFingerprint = fingerprint {
    custom { method, _ -> method.name == "d" && method.definingClass == "Lbc/a;" }
}

internal val installImgurFreeOkHttpInterceptorFingerprint = fingerprint {
    custom { method, _ -> method.name == "e" && method.definingClass == "Lbc/a;" }
}