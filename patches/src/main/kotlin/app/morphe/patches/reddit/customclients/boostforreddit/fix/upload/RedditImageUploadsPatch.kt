/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.upload

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.all.misc.PatchCategory

private const val REDDIT_UPLOAD_PROVIDER = "reddit"

@Suppress("unused")
val redditImageUploadsPatch = morpheBytecodePatch(
    name = "Use Reddit for image uploads",
    description =
        "Uses Boost's native Reddit uploader for image posts and galleries.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.COMMENTS,
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        remoteConfigStringFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    ${setting.checkEnabled("v0", "morphe_native_remote_string")}
                    const-string v0, "uploader_submit"
                    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v0
                    if-nez v0, :morphe_reddit_provider
                    const-string v0, "uploader_submit_multiple"
                    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :morphe_native_remote_string
                    :morphe_reddit_provider
                    const-string v0, "$REDDIT_UPLOAD_PROVIDER"
                    return-object v0
                """,
                ExternalLabel("morphe_native_remote_string", getInstruction(0)),
            )
        }
    }
}
