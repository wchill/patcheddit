package app.morphe.patches.reddit.customclients.sync.syncforreddit.ads

import app.morphe.patches.reddit.customclients.sync.SyncForRedditFreeCompatible
import app.morphe.patches.reddit.customclients.sync.ads.disableAdsPatch

@Suppress("unused")
val disableAdsPatch = disableAdsPatch {
    compatibleWith(SyncForRedditFreeCompatible)
}
