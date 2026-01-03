package app.morphe.patches.reddit.customclients.sync.syncforlemmy.ads

import app.morphe.patches.reddit.customclients.sync.ads.disableAdsPatch
import app.morphe.patches.reddit.customclients.sync.detection.piracy.disablePiracyDetectionPatch

@Suppress("unused")
val disableAdsPatch = disableAdsPatch {
    dependsOn(disablePiracyDetectionPatch)

    compatibleWith("com.laurencedawson.reddit_sync")
}
