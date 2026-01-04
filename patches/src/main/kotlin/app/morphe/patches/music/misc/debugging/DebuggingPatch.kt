package app.morphe.patches.music.misc.debugging

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.music.utils.compatibility.Constants.COMPATIBLE_PACKAGE
import app.morphe.patches.music.utils.patch.PatchList.ENABLE_DEBUG_LOGGING
import app.morphe.patches.music.utils.settings.CategoryType
import app.morphe.patches.music.utils.settings.ResourceUtils.updatePatchStatus
import app.morphe.patches.music.utils.settings.addSwitchPreference
import app.morphe.patches.music.utils.settings.settingsPatch

@Suppress("unused")
val debuggingPatch = resourcePatch(
    ENABLE_DEBUG_LOGGING.title,
    ENABLE_DEBUG_LOGGING.summary,
    // Unlike YouTube, YouTube Music's Litho components do not change very often.
    // That's why it seems better to selectively include patches for only those users who need them.
    false,
) {
    compatibleWith(COMPATIBLE_PACKAGE)

    dependsOn(settingsPatch)

    execute {
        addSwitchPreference(
            CategoryType.MISC,
            "rvx_morphed_debug",
            "false"
        )
        addSwitchPreference(
            CategoryType.MISC,
            "rvx_morphed_debug_protobuffer",
            "false",
            "rvx_morphed_debug"
        )
        addSwitchPreference(
            CategoryType.MISC,
            "rvx_morphed_debug_spannable",
            "false",
            "rvx_morphed_debug"
        )

        updatePatchStatus(ENABLE_DEBUG_LOGGING)

    }
}
