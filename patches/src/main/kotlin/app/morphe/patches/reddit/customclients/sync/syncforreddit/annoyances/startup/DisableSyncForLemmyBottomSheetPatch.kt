package app.morphe.patches.reddit.customclients.sync.syncforreddit.annoyances.startup

import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheBytecodePatch

@Suppress("unused")
val disableSyncForLemmyBottomSheetPatch = morpheBytecodePatch(
    name = "Disable Sync for Lemmy bottom sheet",
    description = "Disables the bottom sheet at the startup that asks you to signup to \"Sync for Lemmy\".",
    default = true,
    extensionPatch = ExtensionPatches.Sync,
) { setting ->
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        setting.patchMethod(MainActivityOnCreateFingerprint) {
            val showBottomSheetIndex = implementation!!.instructions.lastIndex - 1

            removeInstruction(showBottomSheetIndex)
        }
    }
}
