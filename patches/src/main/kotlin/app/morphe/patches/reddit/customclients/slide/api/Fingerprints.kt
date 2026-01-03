package app.morphe.patches.reddit.customclients.slide.api

import app.morphe.patcher.fingerprint

internal val getClientIdFingerprint = fingerprint {
    custom { method, classDef ->
        if (!classDef.endsWith("Credentials;")) return@custom false

        method.name == "getClientId"
    }
}
