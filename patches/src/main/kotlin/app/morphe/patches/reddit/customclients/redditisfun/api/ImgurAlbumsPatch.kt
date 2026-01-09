package app.morphe.patches.reddit.customclients.redditisfun.api

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.redditisfun.misc.extension.sharedExtensionPatch

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/redditisfun/ImgurAlbumsPatch;"

val imgurAlbumsPatch = bytecodePatch(
    name = "Use public imgur API",
    description = "Fix imgur albums not loading."
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(
        "com.andrewshu.android.reddit"("5.6.22")
    )

    execute {
        val m = imgurApiFingerprint.method
        m.addInstructions(0, """
            invoke-static       { p0, p1 }, $EXTENSION_CLASS_DESCRIPTOR->buildImgurUri(Ljava/lang/String;Z)Landroid/net/Uri;
            move-result-object p0
            return-object p0
        """)
    }
}
