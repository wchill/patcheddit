/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.addMorpheSettings
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import com.android.tools.smali.dexlib2.formatter.DexFormatter

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/redditisfun/ImgurAlbumsPatch;"

val imgurAlbumsPatch = morpheBytecodePatch(
    name = "Use public imgur API",
    description = "Fixes Imgur album and gallery loading, thumbnails, and links with titles.",
    default = true,
    extensionPatch = ExtensionPatches.RIF,
    settingsSummary = "Loads Imgur albums and galleries using the public Imgur API.",
) { setting ->
    compatibleWith(*AppCompatibility.RedditIsFun)
    val thumbnails = addMorpheSettings(
        extensionPatch = ExtensionPatches.RIF,
        title = "Fix Imgur album thumbnails",
        description = "Restores album and gallery thumbnails, including albums without a cover.",
        category = PatchCategory.MEDIA,
    )
    val titles = addMorpheSettings(
        extensionPatch = ExtensionPatches.RIF,
        title = "Fix Imgur links with titles",
        description = "Opens title-bearing Imgur album and gallery links like their plain-ID links.",
        category = PatchCategory.MEDIA,
    )

    execute {
        val targets = resolveImgurTargets(buildList { classDefForEach(::add) })
        Fingerprint(
            definingClass = EXTENSION_CLASS_DESCRIPTOR,
            name = "getClientAuthorization",
        ).method.addInstructions(0, """
            const-string v0, ${DexFormatter.INSTANCE.getQuotedString(targets.authorization)}
            return-object v0
        """)
        setting.returnEnabled(Fingerprint(
            definingClass = EXTENSION_CLASS_DESCRIPTOR,
            name = "usePublicApi",
        ).method)
        thumbnails.returnEnabled(Fingerprint(
            definingClass = EXTENSION_CLASS_DESCRIPTOR,
            name = "recoverThumbnails",
        ).method)
        titles.returnEnabled(Fingerprint(
            definingClass = EXTENSION_CLASS_DESCRIPTOR,
            name = "normalizeAlbumLinks",
        ).method)
        setting.patchMethod(ImgurApiFingerprint) {
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
