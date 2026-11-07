/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.sidebartrending

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import app.morphe.patches.all.misc.PatchCategory

@Suppress("unused")
val boostSidebarTrendingTodayPatch = morpheBytecodePatch(
    name = "Restore Boost sidebar Trending today",
    description = "Restores the sidebar's Trending today section, falling back to hot posts, and fixes its community limit setting.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.FEED,
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        /*
         * TrendingTodayFragment -> TrendingTodayViewModel -> rd3 -> pd2.y().
         * Keep Boost's background AsyncTask, but replace its body with a
         * native-first loader that falls back only when the legacy endpoint
         * returns no rows containing submissions.
         */
        setting.patchMethod(trendingTodayAsyncLoaderFingerprint) {
            addInstructions(
                0,
                """
                    invoke-static {}, Lapp/morphe/extension/boost/sidebar/SidebarTrendingPosts;->load()Ljava/util/List;
                    move-result-object p1
                    return-object p1
                """,
            )
        }

        /*
         * The sidebar is global on Home/All/Popular, where "Limit to Community"
         * has no valid context. Remove it there. When W1() resolves a concrete
         * subreddit, retain it as "Limit to r/<name>" with the value preloaded.
         */
        trendingTodayFragmentOnViewCreatedFingerprint.wrapHook(
            setting = setting,
            before = "invoke-static {p0, p1}, Lapp/morphe/extension/boost/sidebar/SidebarTrendingPosts;->configureSearchOptions(Ljava/lang/Object;Landroid/view/View;)V",
            after = "invoke-static {p0}, Lapp/morphe/extension/boost/sidebar/SidebarTrendingPosts;->triggerLoad(Ljava/lang/Object;)V",
        )

        /*
         * Binding is now inlined into the adapter. Preserve the holder and
         * obtain the actual row before the native method reuses its arguments.
         */
        trendingTodayViewHolderBindFingerprint.wrapHook(
            setting = setting,
            before = """
                iget-object v0, p0, ${BoostApi.TrendingTodayAdapter.rows}
                invoke-interface {v0, p2}, Ljava/util/List;->get(I)Ljava/lang/Object;
                move-result-object v0
                invoke-static {p1, v0}, Lapp/morphe/extension/boost/sidebar/SidebarTrendingPosts;->recordBoundRow(Ljava/lang/Object;Ljava/lang/Object;)V
            """,
            after = "invoke-static {p1}, Lapp/morphe/extension/boost/sidebar/SidebarTrendingPosts;->applyBoundTextPolicy(Ljava/lang/Object;)V",
        )
    }
}
