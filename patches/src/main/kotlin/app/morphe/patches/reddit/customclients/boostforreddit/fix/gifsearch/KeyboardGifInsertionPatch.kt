/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.gifsearch

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.all.misc.PatchCategory

@Suppress("unused")
val supportKeyboardGifInsertionPatch = morpheBytecodePatch(
    name = "Support keyboard GIF insertion",
    description = "Lets you insert GIFs and images from your keyboard while writing, when the keyboard provides a public link.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.COMMENTS,
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        setting.guardHook("Lapp/morphe/extension/boost/gifsearch/ImeGifInputConnection;", "enableForEditText")
        formattingBarSetEditTextFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, Lapp/morphe/extension/boost/gifsearch/ImeGifInputConnection;->enableForEditText(Landroid/widget/EditText;)V
            """.trimIndent()
        )
    }
}
