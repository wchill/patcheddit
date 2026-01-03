package app.morphe.patches.reddit.customclients.boostforreddit.debug

import app.morphe.patcher.fingerprint

internal val exceptionHandlerFingerprint = fingerprint {
    custom { method, classDef ->
        method.definingClass == "Lhe/h0;" && method.name == "j0"
    }
}

internal val timberFingerprint = fingerprint {
    custom { method, classDef ->
        method.definingClass == "Lch/a;" && method.name == "a"
    }
}