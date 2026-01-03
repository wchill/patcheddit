package app.morphe.patches.reddit.customclients.infinityforreddit.api

import app.morphe.patcher.fingerprint

internal val apiUtilsFingerprint = fingerprint {
    strings("native-lib")
}