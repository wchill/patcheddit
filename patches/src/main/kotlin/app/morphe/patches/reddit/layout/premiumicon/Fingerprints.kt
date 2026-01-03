package app.morphe.patches.reddit.layout.premiumicon

import app.morphe.patcher.Fingerprint

internal val hasPremiumIconAccessFingerprint = Fingerprint(
    returnType = "Z",
    custom = { method, classDef ->
        classDef.endsWith("MyAccount;") && method.name == "isPremiumSubscriber"
    }
)
