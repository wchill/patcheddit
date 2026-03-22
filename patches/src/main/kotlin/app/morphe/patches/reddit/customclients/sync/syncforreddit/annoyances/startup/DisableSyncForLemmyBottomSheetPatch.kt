package app.morphe.patches.reddit.customclients.sync.syncforreddit.annoyances.startup

import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible

@Suppress("unused")
val disableSyncForLemmyBottomSheetPatch = bytecodePatch(
    name = "Disable Sync for Lemmy bottom sheet",
    description = "Disables the bottom sheet at the startup that asks you to signup to \"Sync for Lemmy\".",
    default = true
) {
    compatibleWith(*SyncForRedditCompatible)

    execute {
        mainActivityOnCreateFingerprint.method.apply {
            val showBottomSheetIndex = implementation!!.instructions.lastIndex - 1

            removeInstruction(showBottomSheetIndex)
        }
    }
}
