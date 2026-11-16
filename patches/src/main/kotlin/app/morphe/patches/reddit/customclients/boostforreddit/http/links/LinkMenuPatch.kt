/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http.links

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import app.morphe.patches.reddit.customclients.boostforreddit.http.archive.LinkBuildContextMenuFingerprint
import app.morphe.patches.reddit.customclients.boostforreddit.http.archive.OnClickContextMenuFingerprint
import app.morphe.patches.reddit.customclients.boostforreddit.http.archive.SETTINGS_UTILS_EXTENSION_CLASS_DESCRIPTOR
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val QUERY = "Lapp/morphe/extension/boost/utils/LinkQueryParameters;"

internal val boostLinkMenuPatch = bytecodePatch {
    dependsOn(ExtensionPatches.Boost)
    execute {
        LinkBuildContextMenuFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                getReference<MethodReference>()?.toString() == BoostApi.BoostMenuView.setMenuOptions.toString()
            }
            val list = getInstruction<FiveRegisterInstruction>(index).registerD
            // All native menu-building branches converge at setMenuOptions.
            addInstructions(index + 1, """
                invoke-static {v$list}, $SETTINGS_UTILS_EXTENSION_CLASS_DESCRIPTOR->addMenuOptions(Ljava/util/List;)V
                invoke-static {p0, v$list}, $QUERY->addMenuOption(${BoostApi.LinkMenu.type}Ljava/util/List;)V
            """)
        }
        OnClickContextMenuFingerprint.method.apply {
            val actions = setOf(BoostApi.Utils.copyLink.toString(), BoostApi.Utils.shareLink.toString())
            val indices = implementation!!.instructions.toList().indices.filter { index ->
                getInstruction(index).getReference<MethodReference>()?.toString() in actions
            }
            check(indices.size == 2) { "Boost link sharing/copying calls changed" }
            for (index in indices.asReversed()) {
                val read = getInstruction<TwoRegisterInstruction>(index - 1)
                check(getInstruction(index - 1).getReference<com.android.tools.smali.dexlib2.iface.reference.FieldReference>()
                    ?.toString() == BoostApi.LinkMenu.url.toString()) { "Boost link URL load changed" }
                addInstructions(index, """
                    invoke-static {v${read.registerB}, v${read.registerA}}, $QUERY->actionUrl(${BoostApi.LinkMenu.type}Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v${read.registerA}
                """)
            }
        }
        OnClickContextMenuFingerprint.wrapHook(before = """
            iget-object v0, p0, ${BoostApi.LinkMenuListener.linkMenu}
            invoke-static {v0, p1}, $QUERY->onClick(${BoostApi.LinkMenu.type}${BoostApi.MenuOption.type})Z
            move-result v0
            if-eqz v0, :morphe_native_link_action
            return-void
            :morphe_native_link_action
        """)
    }
}

@Suppress("unused")
val showStripQueryParameters = morpheBytecodePatch(
    name = "Show strip query parameters option",
    description = "Adds an unchecked query-stripping checkbox to Share link and Copy link menus.",
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.LINKS,
) { setting ->
    dependsOn(boostLinkMenuPatch)
    compatibleWith(*AppCompatibility.Boost)
    execute {
        setting.returnEnabled(mutableClassDefBy(QUERY).methods.single { it.name == "isEnabled" })
        BoostApi.MenuAdapter.onBindViewHolder.fingerprint().wrapHook(after = """
            iget-object v0, p0, ${BoostApi.MenuAdapter.menuView}
            iget-object v0, v0, ${BoostApi.BoostMenuView.options}
            invoke-interface {v0, p2}, Ljava/util/List;->get(I)Ljava/lang/Object;
            move-result-object v0
            check-cast v0, ${BoostApi.MenuOption.type}
            iget-object v1, p1, ${BoostApi.RecyclerViewViewHolder.itemView}
            invoke-static {v1, v0}, $QUERY->bindOption(Landroid/view/View;${BoostApi.MenuOption.type})V
        """)
    }
}
