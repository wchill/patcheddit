/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.gifsearch

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val restoreGifUrlInsertionPatch = morpheBytecodePatch(
    name = "Restore GIF URL insertion",
    description = "Adds an option to the image menu to insert a GIF from its URL.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.COMMENTS,
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        setting.patchMethod(formattingBarImageMenuOptionsFingerprint) {
            val menuViewIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.NEW_INSTANCE &&
                    getReference<TypeReference>()?.type == BoostApi.BoostMenuView.type
            }

            addInstructions(
                menuViewIndex,
                """
                    new-instance v1, ${BoostApi.MenuOption.type}
                    invoke-direct {v1}, ${BoostApi.MenuOption.construct}
                    const v2, 0x7f0a0273
                    iput v2, v1, ${BoostApi.MenuOption.id}
                    const v2, 0x7f1306de
                    iput v2, v1, ${BoostApi.MenuOption.titleResource}
                    const v2, 0x7f0801e8
                    iput v2, v1, ${BoostApi.MenuOption.iconResource}
                    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z
                """,
            )
        }

        formattingBarHandleImageMenuOptionFingerprint.wrapHook(
            setting = setting,
            before = """
                iget v0, p1, ${BoostApi.MenuOption.id}
                const v1, 0x7f0a0273
                if-ne v0, v1, :morphe_native_image_menu
                iget-object v0, p0, ${BoostApi.FormattingImageMenuListener.formattingBar}
                invoke-static {v0}, Lapp/morphe/extension/boost/gifsearch/GifUrlInsertion;->open(Ljava/lang/Object;)V
                iget-object v0, v0, ${BoostApi.FormattingBar.imageMenu}
                if-eqz v0, :morphe_gif_menu_closed
                invoke-virtual {v0}, ${BoostApi.MaterialDialog.dismiss}
                :morphe_gif_menu_closed
                return-void
                :morphe_native_image_menu
            """,
        )
    }
}
