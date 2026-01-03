package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.slink

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

internal val linkHelperOpenLinkFingerprint = Fingerprint(
    filters = listOf(string("Link title: "))
)

internal val setAuthorizationHeaderFingerprint = Fingerprint(
    returnType = "Ljava/util/HashMap;",
    filters = listOf(string("Authorization"), string("bearer ")),
    custom = { method, _ -> method.definingClass == "Lcom/laurencedawson/reddit_sync/singleton/a;" }
)
