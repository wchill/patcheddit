package app.morphe.patches.reddit.misc.tracking.url

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.PATCH_DESCRIPTION_SANITIZE_SHARING_LINKS
import app.morphe.patches.shared.PATCH_NAME_SANITIZE_SHARING_LINKS

@Suppress("unused")
val sanitizeUrlQueryPatch = bytecodePatch(
    name = PATCH_NAME_SANITIZE_SHARING_LINKS,
    description = PATCH_DESCRIPTION_SANITIZE_SHARING_LINKS,
) {
    compatibleWith("com.reddit.frontpage")

    execute {
        shareLinkFormatterFingerprint.method.addInstructions(
            0,
            "return-object p0",
        )
    }
}
