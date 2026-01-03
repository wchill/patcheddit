package app.morphe.patches.reddit.customclients.sync.syncforreddit.ads

import app.morphe.patches.reddit.customclients.sync.ads.disableAdsPatch

@Suppress("unused")
val disableAdsPatch = disableAdsPatch {
    compatibleWith("io.syncapps.lemmy_sync")
}
