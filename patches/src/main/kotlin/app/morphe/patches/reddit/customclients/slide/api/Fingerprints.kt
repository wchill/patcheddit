package app.morphe.patches.reddit.customclients.slide.api

import app.morphe.patcher.Fingerprint

internal val getClientIdFingerprint = Fingerprint(
    custom = { method, classDef ->
        if (!classDef.endsWith("Credentials;")) return@Fingerprint false

        method.name == "getClientId"
    }
)
