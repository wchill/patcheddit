/*
 * Copyright 2026 brealorg.
 * Modifications Copyright 2026 wchill.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.upload

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches

@Suppress("unused")
val editorImagePreviewPatch = morpheBytecodePatch(
    name = "Preview images in the editor",
    description = "Shows previews of supported image URLs above the comment or text-post editor.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.COMMENTS,
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        setting.guardHook("Lapp/morphe/extension/boost/upload/EditorImagePreview;", "bind")
        formattingBarSetEditTextFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, Lapp/morphe/extension/boost/upload/EditorImagePreview;->bind(Landroid/widget/EditText;)V
            """,
        )
    }
}
