package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.thumbnail

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheBytecodePatch

@Suppress("unused")
val fixPostThumbnailsPatch = morpheBytecodePatch(
    name = "Fix post thumbnails",
    description = "Fixes loading post thumbnails by correcting their URLs.",
    default = true,
    extensionPatch = ExtensionPatches.Sync,
) { setting ->

    compatibleWith(*AppCompatibility.SyncForReddit)

    // Image URLs contain escaped ampersands (&amp;), let's replace these with unescaped ones (&).
    execute {
        setting.patchMethod(CustomImageViewLoadFingerprint) {
            addInstructions(
                0,
                """
                    const-string v0, "&amp;"
                    const-string v1, "&"
                    invoke-virtual {p1, v0, v1}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
                    move-result-object p1
                """,
            )
        }
    }
}
