/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.api

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/redditisfun/ImgurAlbumsPatch;"

val imgurAlbumsPatch = bytecodePatch(
    name = "Use public imgur API",
    description = "Fix imgur albums not loading.",
    default = true
) {
    dependsOn(ExtensionPatches.RIF)
    compatibleWith(*AppCompatibility.RedditIsFun)

    execute {
        val m = ImgurApiFingerprint.method
        m.addInstructions(0, """
            invoke-static       { p0, p1 }, $EXTENSION_CLASS_DESCRIPTOR->buildImgurUri(Ljava/lang/String;Z)Landroid/net/Uri;
            move-result-object p0
            return-object p0
        """)
    }
}
