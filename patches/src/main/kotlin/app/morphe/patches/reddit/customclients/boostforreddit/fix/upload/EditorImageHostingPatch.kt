/*
 * Copyright 2026 brealorg.
 * Modifications Copyright 2026 wchill.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.upload

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.util.ResourceGroup

private val editorImageHostingResourcesPatch = addResourcesPatch(
    "boost",
    ResourceGroup("xml", "morphe_boost_editor_image_hosting.xml"),
    ResourceGroup("layout", "morphe_boost_imgbb_api_key_dialog.xml"),
)

@Suppress("unused")
val editorImageHostingPatch = morpheBytecodePatch(
    name = "Configure editor image hosting",
    description =
        "Uses Imgur Free or your configured ImgBB account for images attached to comments and text posts.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.COMMENTS,
    optionsResource = "morphe_boost_editor_image_hosting_entry",
    resourceDirectory = "boost",
) { setting ->
    dependsOn(editorImageHostingResourcesPatch)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        remoteConfigStringFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    ${setting.checkEnabled("v0", "morphe_native_editor_provider")}
                    const-string v0, "uploader_format"
                    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :morphe_native_editor_provider
                    invoke-static {}, Lapp/morphe/extension/boost/upload/ExternalImageUploadSettings;->getEditorProvider()Ljava/lang/String;
                    move-result-object v0
                    return-object v0
                """,
                ExternalLabel("morphe_native_editor_provider", getInstruction(0)),
            )
        }
        mediaUploaderFactoryFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    ${setting.checkEnabled("v0", "morphe_native_upload_factory")}
                    const-string v0, "imgbb"
                    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :morphe_native_upload_factory
                    invoke-static {}, Lapp/morphe/extension/boost/upload/ExternalImageUploadFactory;->createImgBbUploader()${returnType}
                    move-result-object v0
                    return-object v0
                """,
                ExternalLabel("morphe_native_upload_factory", getInstruction(0)),
            )
        }
    }
}
