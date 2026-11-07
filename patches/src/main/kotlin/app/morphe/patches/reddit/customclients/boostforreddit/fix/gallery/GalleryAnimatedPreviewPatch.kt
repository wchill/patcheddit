/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.gallery

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import app.morphe.patches.all.misc.PatchCategory

private const val GALLERY_PREVIEW_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boost/gallery/GalleryAnimatedPreview;"

@Suppress("unused")
val galleryAnimatedPreviewPatch = morpheBytecodePatch(
    name = "Animate media in Boost gallery previews",
    description = "Plays GIFs and videos in Reddit gallery previews, following Boost's data saving settings. Tapping still opens the full-screen viewer.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.MEDIA,
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        for (name in listOf("selectPage", "setPagerState")) {
            setting.guardHook(GALLERY_PREVIEW_EXTENSION_DESCRIPTOR, name)
        }
        /*
         * GalleryActivity keeps two pages on either side alive. Its legacy
         * FragmentStatePagerAdapter therefore cannot be used as a reliable
         * RESUMED/PAUSED visibility signal. Hook the authoritative
         * ViewPager.OnPageChangeListener.onPageSelected callback instead.
         */
        galleryPageSelectedFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, ${BoostApi.GalleryPageListener.activity}
                invoke-static {v0, p1}, Lapp/morphe/extension/boost/gallery/GalleryAnimatedPreview;->selectPage(Ljava/lang/Object;I)V
            """.trimIndent(),
        )

        /*
         * Do not create a player for an intermediate page while ViewPager is
         * dragging or settling. p0 is otherwise unused by this no-op callback,
         * so it can safely hold the activity without adding a local register.
         */
        galleryPagerStateFingerprint.method.addInstructions(
            0,
            """
                iget-object p0, p0, ${BoostApi.GalleryPageListener.activity}
                invoke-static {p0, p1}, Lapp/morphe/extension/boost/gallery/GalleryAnimatedPreview;->setPagerState(Ljava/lang/Object;I)V
            """.trimIndent(),
        )

        galleryPageFactoryFingerprint.wrapHook(
            setting = setting,
            before = """
                iget-object v1, p0, ${BoostApi.GalleryActivityImagePagerAdapter.activity}
                invoke-static {v1, p1}, Lapp/morphe/extension/boost/gallery/GalleryAnimatedPreview;->beginPage(Ljava/lang/Object;I)V
            """,
            after = """
                iget-object v1, p0, ${BoostApi.GalleryActivityImagePagerAdapter.activity}
                invoke-static {v1, v0}, Lapp/morphe/extension/boost/gallery/GalleryAnimatedPreview;->registerPage(Ljava/lang/Object;Ljava/lang/Object;)V
            """,
        )

        imageModelFragmentOnCreateViewFingerprint.wrapHook(
            setting = setting,
            after = """
                invoke-static {p0, v0}, $GALLERY_PREVIEW_EXTENSION_DESCRIPTOR->bindPage(Ljava/lang/Object;Landroid/view/View;)V
            """,
        )

        imageModelFragmentOnDestroyFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p0}, $GALLERY_PREVIEW_EXTENSION_DESCRIPTOR->unbindPage(Ljava/lang/Object;)V
            """.trimIndent(),
        )
    }
}
