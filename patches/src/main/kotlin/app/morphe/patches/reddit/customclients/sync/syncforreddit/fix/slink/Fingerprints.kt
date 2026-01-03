package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.slink

import app.morphe.patcher.Fingerprint

internal val linkHelperOpenLinkFingerprint = Fingerprint(
    strings = listOf("Link title: ")
)

internal val setAuthorizationHeaderFingerprint = Fingerprint(
    returnType = "Ljava/util/HashMap;",
    strings = listOf("Authorization", "bearer "),
    custom = { method, _ -> method.definingClass == "Lcom/laurencedawson/reddit_sync/singleton/a;" }
)
