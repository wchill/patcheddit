/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.search

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import app.morphe.patches.all.misc.PatchCategory

@Suppress("unused")
val boostSearchDefaultDiscoveryPatch = morpheBytecodePatch(
    name = "Boost search default discovery",
    description = "Opens Search right away on a list of active subreddits.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.FEED,
    optionsResource = "morphe_boost_search_discovery_options",
    resourceDirectory = "boost",
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        setting.guardHook("Lapp/morphe/extension/boost/search/SearchExploreRows;", "appendOrRefresh")
        /*
         * Normal Boost search opens SearchGenericActivity.
         * s1() builds the empty-state "Go to..." section and already adds Random.
         * v5m keeps Random, displays cached active subreddits instantly, refreshes source-specific popular/HOT posts, and renames the Reddit search filter from Community to Subreddit.
         */
        searchGenericActivityDefaultGoToFingerprint.method.apply {
            /*
             * Insert at method entry. The previous tail insertion was still
             * branch-skippable because Boost's cond label stayed attached to
             * return-void. Entry injection cannot be skipped by a native branch.
             */
            addInstructions(
                0,
                """
                    iget-object v0, p0, ${BoostApi.SearchAbstractActivity.rows}
                    invoke-static {p0, v0}, Lapp/morphe/extension/boost/search/SearchExploreRows;->appendOrRefresh(Landroid/app/Activity;Ljava/util/ArrayList;)V
                """.trimIndent(),
            )
        }

        /*
         * Explore/default state should not open with the keyboard covering half
         * the landing content. Tapping the search field still focuses normally.
         */
        searchGenericActivityOnCreateFingerprint.wrapHook(
            setting = setting,
            after = """
                invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                move-result-object v0
                const/4 v1, 0x3
                invoke-virtual {v0, v1}, Landroid/view/Window;->setSoftInputMode(I)V
                iget-object v0, p0, ${BoostApi.SearchAbstractActivity.searchEditText}
                invoke-virtual {v0}, Landroid/view/View;->clearFocus()V
            """,
        )

    }
}
