package app.morphe.patches.reddit.customclients.boostforreddit.http.archive

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch


internal val SETTINGS_UTILS_EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/boostforreddit/utils/SettingsUtils;"


@Suppress("unused")
val addArchiveLinks = bytecodePatch(
    name="Add archive links to context menu"
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith("com.rubenmayayo.reddit")
    execute {
        linkBuildContextMenuFingerprint.method.apply {
            val index = linkBuildContextMenuFingerprint.instructionMatches.lastIndex
            addInstructions(
                index,
                """
                invoke-static       {v0}, $SETTINGS_UTILS_EXTENSION_CLASS_DESCRIPTOR->addMenuOptions(Ljava/util/List;)V
                """
            )
        }

        onClickContextMenuFingerprint.method.apply {
            val index = onClickContextMenuFingerprint.instructionMatches.lastIndex
            val openLinkDialogName = "Lcom/rubenmayayo/reddit/ui/customviews/t;"
            val navigationName = "Lcom/rubenmayayo/reddit/ui/activities/i;"
            val openUriName = "d0"
            addInstructionsWithLabels(
                index,
                """
                const/16            v1, 101
                if-eq               v0, v1, :wayback
                const/16            v1, 102
                if-eq               v0, v1, :archive
                goto                :continue
                :wayback
                const-string        v0, "https://web.archive.org/web/"
                goto                :action
                :archive
                const-string        v0, "https://archive.is/"
                :action
                iget-object         p1, p0, $openLinkDialogName->a:Landroid/content/Context;
                iget-object         v1, p0, $openLinkDialogName->c:Ljava/lang/String;
                invoke-virtual      {v0, v1}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
                move-result-object  v0
                invoke-static       {p1, v0}, $navigationName->$openUriName(Landroid/content/Context;Ljava/lang/String;)V
                return-void
                """,
                ExternalLabel("continue", getInstruction(index))
            )
        }
    }
}