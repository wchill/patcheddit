package app.morphe.patches.reddit.customclients.infinityforreddit.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

internal val apiUtilsFingerprint = Fingerprint(
    filters = listOf(string("native-lib"))
)
