package app.morphe.patches.reddit.layout.premiumicon

import app.morphe.patcher.fingerprint

internal val hasPremiumIconAccessFingerprint = fingerprint {
    returns("Z")
    custom { method, classDef ->
        classDef.endsWith("MyAccount;") && method.name == "isPremiumSubscriber"
    }
}
