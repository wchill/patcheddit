package app.morphe.patches.reddit.customclients.sync.syncforlemmy.ads

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.sync.ads.disableAdsPatch
import app.morphe.patches.reddit.customclients.sync.detection.piracy.disablePiracyDetectionPatch

@Suppress("unused")
val disableAdsPatch = disableAdsPatch(ExtensionPatches.SyncForLemmy) {
    dependsOn(disablePiracyDetectionPatch)
    compatibleWith(AppCompatibility.SyncForLemmy)
}
