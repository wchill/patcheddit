package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.slink

import app.morphe.patcher.Fingerprint

internal object LinkHelperOpenLinkFingerprint : Fingerprint(
    strings = listOf("Link title: ")
)

internal object SetAuthorizationHeaderFingerprint : Fingerprint(
    definingClass = "Lcom/laurencedawson/reddit_sync/singleton/a;",
    returnType = "Ljava/util/HashMap;",
    strings = listOf("Authorization", "bearer "),
)
