/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import com.android.tools.smali.dexlib2.formatter.DexFormatter

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/redditisfun/ImgurAlbumsPatch;"

val imgurAlbumsPatch = morpheBytecodePatch(
    name = "Use public imgur API",
    description = "Fixes Imgur album and gallery loading, thumbnails, and links with titles.",
    default = true,
    extensionPatch = ExtensionPatches.RIF,
) { setting ->
    compatibleWith(*AppCompatibility.RedditIsFun)

    execute {
        // Reuse native Imgur authorization instead of embedding a client ID in the patch.
        val targets = resolveImgurTargets(buildList { classDefForEach(::add) })
        Fingerprint(
            definingClass = EXTENSION_CLASS_DESCRIPTOR,
            name = "getClientAuthorization",
        ).method.addInstructions(0, """
            const-string v0, ${DexFormatter.INSTANCE.getQuotedString(targets.authorization)}
            return-object v0
        """)
        // All HTTP repairs follow the existing Imgur switch and its saved value.
        for (hook in listOf("usePublicApi", "recoverThumbnails", "normalizeAlbumLinks")) {
            setting.returnEnabled(Fingerprint(
                definingClass = EXTENSION_CLASS_DESCRIPTOR,
                name = hook,
            ).method)
        }
        // The interceptor repairs thumbnails/title IDs; this hook redirects full-album requests.
        setting.patchMethod(ImgurApiFingerprint) {
            // p0/p1 are the album ID/gallery flag; return the new URI without replacing native parsing.
            addInstructions(
                0,
                """
                    invoke-static {p0, p1}, $EXTENSION_CLASS_DESCRIPTOR->buildImgurUri(Ljava/lang/String;Z)Landroid/net/Uri;
                    move-result-object p0
                    return-object p0
                """,
            )
        }
    }
}
